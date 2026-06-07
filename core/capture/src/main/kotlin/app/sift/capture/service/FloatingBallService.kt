package app.sift.capture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.sift.capture.CaptureCoordinator
import app.sift.capture.MediaProjectionHolder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

/**
 * 常驻悬浮球。点击行为：
 * - 若投屏 projection 已存活（[MediaProjectionHolder.isActive]）→ 直接命令截屏服务抓帧，**不弹授权框**。
 * - 否则 → 拉起 [app.sift.CaptureTriggerActivity] 申请一次投屏授权（之后就复用）。
 *
 * 支持拖动；用位移阈值区分"拖动"与"点击"。v0.1：用一个圆形 ImageView 当球，需真机验证。
 */
@AndroidEntryPoint
class FloatingBallService : Service() {

    @Inject
    lateinit var holder: MediaProjectionHolder

    private lateinit var windowManager: WindowManager
    private var ballView: View? = null
    private var lastTapUptime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        addBall()
    }

    private fun addBall() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val view = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 24, 24, 24)
        }
        attachDragAndClick(view, params)
        windowManager.addView(view, params)
        ballView = view
    }

    private fun attachDragAndClick(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > CLICK_SLOP || abs(dy) > CLICK_SLOP) moved = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) triggerCapture()
                    true
                }

                else -> false
            }
        }
    }

    private fun triggerCapture() {
        // 防抖：免费档 LLM 有速率限制，连点会撞 429；间隔太短的点击直接忽略
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTapUptime < MIN_TAP_INTERVAL_MS) {
            Toast.makeText(this, "别太快～正在处理上一张", Toast.LENGTH_SHORT).show()
            return
        }
        lastTapUptime = now

        acknowledgeTap()
        if (holder.isActive) {
            // 已授权：先把悬浮球藏起来再抓帧，避免相机图标被拍进截图
            captureWithBallHidden()
        } else {
            // 首次：拉起透明 Activity 申请投屏授权（捕获时机由用户在弹窗决定，不做隐藏舞蹈）
            startActivity(
                Intent(CaptureCoordinator.ACTION_TRIGGER_CAPTURE)
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 隐藏悬浮球 → 等它从画面消失 → 抓帧（拿无球的新鲜帧）→ 稍后恢复球。 */
    private fun captureWithBallHidden() {
        val v = ballView ?: run {
            ContextCompat.startForegroundService(this, ScreenCaptureService.captureIntent(this, null))
            return
        }
        v.visibility = View.INVISIBLE
        v.postDelayed({
            ContextCompat.startForegroundService(this, ScreenCaptureService.captureIntent(this, null))
        }, BALL_HIDE_DELAY_MS)
        v.postDelayed({ ballView?.visibility = View.VISIBLE }, BALL_RESTORE_DELAY_MS)
    }

    /** 点击即时反馈：震动 + 缩放动画 + Toast，让用户知道点到了（分析结果稍后由通知给出）。 */
    private fun acknowledgeTap() {
        ballView?.let { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            v.animate().scaleX(0.7f).scaleY(0.7f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
        }
        Toast.makeText(this, "Sift 正在分析当前画面…", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        ballView?.let { runCatching { windowManager.removeView(it) } }
        ballView = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sift 悬浮球", NotificationManager.IMPORTANCE_MIN),
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sift 悬浮球已开启")
            .setContentText("刷到好内容，点一下悬浮球")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1002
        private const val CHANNEL_ID = "sift_floating_ball"
        private const val CLICK_SLOP = 12 // px，超过视为拖动
        private const val MIN_TAP_INTERVAL_MS = 2000L // 防抖，避免连点撞免费档限流
        private const val BALL_HIDE_DELAY_MS = 120L // 藏球后等渲染再抓帧
        private const val BALL_RESTORE_DELAY_MS = 700L // 抓帧后恢复球（留足余量）
    }
}
