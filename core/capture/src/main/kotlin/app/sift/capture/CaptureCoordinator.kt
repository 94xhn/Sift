package app.sift.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 一次截屏的产物：base64 编码图 + 来源 App 包名。 */
data class Screenshot(
    val base64: String,
    val mimeType: String,
    val sourceApp: String?,
)

/**
 * 进程内事件总线：截屏服务产出 [Screenshot] → 上层（app）收到后喂给 CaptureAgent。
 * 用 Hilt 单例保证服务与 app 拿到同一个实例。
 */
@Singleton
class CaptureCoordinator @Inject constructor() {
    private val _screenshots = MutableSharedFlow<Screenshot>(extraBufferCapacity = 8)
    val screenshots: SharedFlow<Screenshot> = _screenshots.asSharedFlow()

    fun publish(shot: Screenshot) {
        _screenshots.tryEmit(shot)
    }

    companion object {
        /** 悬浮球点击 → app 侧 Activity 申请投屏权限的 intent action。 */
        const val ACTION_TRIGGER_CAPTURE = "app.sift.action.TRIGGER_CAPTURE"
    }
}
