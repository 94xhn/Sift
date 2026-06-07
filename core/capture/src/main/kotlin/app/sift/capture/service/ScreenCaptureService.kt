package app.sift.capture.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import app.sift.capture.CaptureCoordinator
import app.sift.capture.MediaProjectionHolder
import app.sift.capture.Screenshot
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 常驻截屏前台服务。
 *
 * Android 14+ 的硬限制：一个 [MediaProjection] 实例【只能 createVirtualDisplay 一次】，
 * 且 token 不可复用。因此模型是：
 *   - 首次（ACTION_INIT）：用 token 建 projection，**建立唯一一个常驻 VirtualDisplay + ImageReader**，
 *     让它持续镜像屏幕；监听器不断 drain 帧保持画面最新。
 *   - 每次点球（ACTION_CAPTURE）：只置一个"待抓"标志，由常驻监听器在下一帧把当前画面发出去——
 *     不再新建 VirtualDisplay（否则崩 SecurityException）。
 *   - 关闭（ACTION_STOP）：释放 display/reader/projection。
 *
 * 截图用完即删（base64 走内存总线，不写盘）。
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject
    lateinit var coordinator: CaptureCoordinator

    @Inject
    lateinit var holder: MediaProjectionHolder

    private val bgThread = HandlerThread("sift-capture").apply { start() }
    private val bgHandler = Handler(bgThread.looper)

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var width = 0
    private var height = 0

    private val captureRequested = AtomicBoolean(false)

    /** 最近一帧的位图快照（仅在 bgHandler 线程读写）。点球时直接用它，静止画面也能立即截。 */
    private var latestBitmap: Bitmap? = null
    private var lastConvertUptime = 0L

    @Volatile
    private var pendingSourceApp: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        when (intent?.action) {
            ACTION_INIT -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                if (resultCode != Activity.RESULT_OK || data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!holder.isActive) {
                    startMirroring(resultCode, data)
                }
                requestCapture(intent.getStringExtra(EXTRA_SOURCE_APP))
            }

            ACTION_CAPTURE -> {
                if (holder.isActive && virtualDisplay != null) {
                    requestCapture(intent.getStringExtra(EXTRA_SOURCE_APP))
                } else {
                    stopSelf()
                }
            }

            ACTION_STOP -> {
                cleanup()
                stopForegroundCompat()
                stopSelf()
            }

            else -> stopSelf()
        }
        // 不用 STICKY：被杀后 projection 已失效，重启也无意义
        return START_NOT_STICKY
    }

    /** 建立唯一一个常驻 VirtualDisplay + ImageReader，持续镜像屏幕。只在首次调用。 */
    private fun startMirroring(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(resultCode, data)
        // Android 14+ 要求注册回调，否则 createVirtualDisplay 抛异常
        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    cleanup()
                    stopForegroundCompat()
                    stopSelf()
                }
            },
            bgHandler,
        )
        holder.set(mp)

        val metrics = resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader.setOnImageAvailableListener({ r ->
            // 关键：拿到帧后立刻复制成 Bitmap 再【马上 close】，绝不长期持有 Image，
            // 否则 maxImages 很快耗尽，刷屏 "Unable to acquire a buffer item" 且画面卡死。
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val now = android.os.SystemClock.uptimeMillis()
                if (captureRequested.get() || now - lastConvertUptime >= CONVERT_THROTTLE_MS) {
                    latestBitmap = image.toBitmap(width, height)
                    lastConvertUptime = now
                }
            } catch (e: Exception) {
                // 单帧转换失败忽略
            } finally {
                image.close()
            }
            // 若有挂起的截图请求（第一帧还没来就点了球），现在补发
            if (captureRequested.compareAndSet(true, false)) {
                latestBitmap?.let { publishBitmap(it) }
            }
        }, bgHandler)
        imageReader = reader

        // 整个会话只 createVirtualDisplay 这一次
        virtualDisplay = mp.createVirtualDisplay(
            "sift-capture",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, bgHandler,
        )
    }

    private fun requestCapture(sourceApp: String?) {
        pendingSourceApp = sourceApp
        // 优先抓"下一帧"——这样隐藏悬浮球后拿到的是无球的新鲜帧；
        // 若画面完全静止、FRESH_FRAME_FALLBACK_MS 内没有新帧，则用缓存帧兜底。
        captureRequested.set(true)
        bgHandler.postDelayed({
            if (captureRequested.compareAndSet(true, false)) {
                latestBitmap?.let { publishBitmap(it) }
            }
        }, FRESH_FRAME_FALLBACK_MS)
    }

    /** 把一帧位图编码成 base64 发出去。 */
    private fun publishBitmap(bitmap: Bitmap) {
        try {
            val base64 = bitmap.downscale(MAX_DIM).toBase64Jpeg()
            coordinator.publish(Screenshot(base64, "image/jpeg", pendingSourceApp))
        } catch (e: Exception) {
            // 单帧失败忽略，下次再来
        }
    }

    private fun cleanup() {
        captureRequested.set(false)
        latestBitmap = null
        virtualDisplay?.release(); virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close(); imageReader = null
        holder.clear()
    }

    override fun onDestroy() {
        cleanup()
        bgThread.quitSafely()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sift 截屏", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sift 截屏已就绪")
            .setContentText("点悬浮球即可捕获当前屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "sift_capture"
        private const val MAX_DIM = 1280 // 下采样上限，省 token + 加速
        private const val CONVERT_THROTTLE_MS = 300L // 镜像帧转 Bitmap 的节流，省 CPU
        private const val FRESH_FRAME_FALLBACK_MS = 400L // 等不到新帧（静止画面）就用缓存帧兜底

        const val ACTION_INIT = "app.sift.capture.action.INIT"
        const val ACTION_CAPTURE = "app.sift.capture.action.CAPTURE"
        const val ACTION_STOP = "app.sift.capture.action.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_SOURCE_APP = "source_app"

        /** 首次：带投屏 token 建立常驻镜像，并抓第一帧。 */
        fun initIntent(context: Context, resultCode: Int, data: Intent, sourceApp: String?): Intent =
            Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_INIT)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_SOURCE_APP, sourceApp)

        /** 后续：从常驻镜像取一帧（不新建 display、不重新授权）。 */
        fun captureIntent(context: Context, sourceApp: String?): Intent =
            Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_CAPTURE)
                .putExtra(EXTRA_SOURCE_APP, sourceApp)

        /** 释放镜像与 projection 并停止服务。 */
        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}

private fun android.media.Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val buffer = plane.buffer
    // rewind：同一帧可能被复用（静止画面多次截图），不 rewind 第二次读会拿到空数据
    buffer.rewind()
    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888,
    )
    bitmap.copyPixelsFromBuffer(buffer)
    return if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)
}

private fun Bitmap.downscale(maxDim: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxDim) return this
    val scale = maxDim.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
}

private fun Bitmap.toBase64Jpeg(): String {
    val baos = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 85, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
