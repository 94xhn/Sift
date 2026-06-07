package app.sift.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import app.sift.domain.agent.CaptureAgent
import app.sift.domain.llm.LLMProvider
import app.sift.domain.llm.LlmConfig
import app.sift.domain.model.CaptureRequest
import app.sift.domain.model.CaptureResult
import app.sift.domain.repository.NoteRepository
import app.sift.domain.repository.SettingsRepository
import app.sift.domain.repository.UsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 截图处理管线：订阅 [CaptureCoordinator] → 调 [CaptureAgent] → 落库 + 记统计 → 通知用户。
 * 进程级单例，在 [app.sift.SiftApp.onCreate] 启动后常驻消费。
 */
@Singleton
class CaptureProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: CaptureCoordinator,
    private val agent: CaptureAgent,
    private val provider: LLMProvider,
    private val notes: NoteRepository,
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<CaptureResult>(extraBufferCapacity = 8)
    val events: SharedFlow<CaptureResult> = _events.asSharedFlow()

    /** 保留最近一次结果，供 UI 在用户回到 App 后展示（通知被吞也能看到）。 */
    private val _lastResult = MutableStateFlow<CaptureResult?>(null)
    val lastResult: StateFlow<CaptureResult?> = _lastResult.asStateFlow()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        Log.i(TAG, "CaptureProcessor started, listening for screenshots")
        scope.launch {
            coordinator.screenshots.collect { shot ->
                Log.i(TAG, "screenshot received: base64Len=${shot.base64.length}, source=${shot.sourceApp}")
                runCatching { handle(shot) }
                    .onFailure { Log.e(TAG, "handle() threw", it) }
            }
        }
    }

    private suspend fun handle(shot: Screenshot) {
        val key = settings.getApiKey()
        if (key.isNullOrBlank()) {
            Log.w(TAG, "no API key configured -> Failed")
            notify("未配置 API Key", "请先在设置里填写 Base URL / 模型 / Key")
            emit(CaptureResult.Failed("未配置 API Key，请先在设置里填写"))
            return
        }
        val config = LlmConfig(
            baseUrl = settings.getBaseUrl(),
            apiKey = key,
            model = settings.getModel(),
        )
        Log.i(TAG, "calling agent: model=${config.model}, baseUrl=${config.baseUrl}, keyLen=${key.length}")
        val request = CaptureRequest(
            imageBase64 = shot.base64,
            mimeType = shot.mimeType,
            sourceApp = shot.sourceApp,
            knownCategories = notes.knownCategories(),
        )

        val result = agent.run(request, provider, config)
        val today = LocalDate.now().toString()
        when (result) {
            is CaptureResult.Kept -> {
                Log.i(TAG, "KEPT: ${result.note.title}")
                notes.upsert(result.note)
                usage.recordCapture(today, kept = true)
                notify("已沉淀：${result.note.title}", result.note.summary)
            }

            is CaptureResult.Discarded -> {
                Log.i(TAG, "DISCARDED: ${result.reason}")
                usage.recordCapture(today, kept = false)
                notify("已忽略一张截图", result.reason)
            }

            is CaptureResult.Failed -> {
                Log.e(TAG, "FAILED: ${result.error}")
                notify("处理失败", result.error)
            }
        }
        emit(result)
    }

    private suspend fun emit(result: CaptureResult) {
        _lastResult.value = result
        _events.emit(result)
    }

    private fun notify(title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Sift 捕获结果", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        private const val TAG = "SiftCapture"
        private const val CHANNEL_ID = "sift_result"
        private const val NOTIF_ID = 2001
    }
}
