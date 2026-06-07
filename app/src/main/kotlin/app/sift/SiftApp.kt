package app.sift

import android.app.Application
import app.sift.capture.CaptureProcessor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SiftApp : Application() {

    @Inject
    lateinit var captureProcessor: CaptureProcessor

    override fun onCreate() {
        super.onCreate()
        // 进程存活期间持续消费截图总线，即便 UI 不在前台也能处理。
        captureProcessor.start()
    }
}
