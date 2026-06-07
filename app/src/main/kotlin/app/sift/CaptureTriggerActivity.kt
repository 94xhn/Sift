package app.sift

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.sift.capture.service.ScreenCaptureService

/**
 * 透明 Activity：悬浮球点击后被拉起，向系统申请投屏权限（弹合规提示），
 * 拿到 token 后启动 [ScreenCaptureService] 抓帧，随即结束。
 */
class CaptureTriggerActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    this,
                    ScreenCaptureService.initIntent(this, result.resultCode, data, sourceApp = null),
                )
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
