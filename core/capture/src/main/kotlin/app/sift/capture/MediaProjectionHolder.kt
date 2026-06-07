package app.sift.capture

import android.media.projection.MediaProjection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级持有当前存活的 [MediaProjection]。
 *
 * MediaProjection 的授权 token 是一次性的：每 getMediaProjection 一次就弹一次系统授权框。
 * 因此首次授权后必须把 projection 留住，后续截屏复用同一个实例——只要它没 stop，
 * 就能反复 createVirtualDisplay 抓帧而不再弹框。关闭悬浮球时才 [clear] 释放。
 */
@Singleton
class MediaProjectionHolder @Inject constructor() {

    @Volatile
    var projection: MediaProjection? = null
        private set

    val isActive: Boolean get() = projection != null

    fun set(mp: MediaProjection) {
        projection = mp
    }

    fun clear() {
        projection?.stop()
        projection = null
    }
}
