package app.sift.capture

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject

/**
 * 读取各 App 前台使用时长，为"数据说话"仪表盘提供"今天刷了多久"。
 * 需要 PACKAGE_USAGE_STATS（特殊权限，用户须在系统设置里手动授予）。
 */
class UsageStatsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 常见短视频 App 包名，用于汇总刷视频时长。 */
    private val shortVideoPackages = setOf(
        "com.ss.android.ugc.aweme",      // 抖音
        "com.smile.gifmaker",            // 快手
        "com.ss.android.ugc.aweme.lite", // 抖音极速版
        "com.xingin.xhs",                // 小红书
        "tv.danmaku.bili",               // 哔哩哔哩
    )

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // unsafeCheckOpNoThrow 仅 API 29+；minSdk 26 需对 26~28 走旧方法
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 今天短视频类 App 的前台时长（毫秒）。无权限时返回 0。 */
    fun shortVideoMillisToday(): Long {
        if (!hasPermission()) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfToday()
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return 0L
        return stats
            .filter { it.packageName in shortVideoPackages }
            .sumOf { it.totalTimeInForeground }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
