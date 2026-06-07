package app.sift.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.capture.service.FloatingBallService
import app.sift.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val configured = settings.observeConfigured()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenDashboard: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val configured by vm.configured.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sift", style = MaterialTheme.typography.headlineMedium)
        Text(
            "刷到的好东西，点一下悬浮球，让 Agent 帮你沉淀。",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(4.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (configured) "✅ 已配置 API" else "⚠️ 尚未配置 API，先去设置填 Key",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onOpenSettings) { Text("API 设置") }
            }
        }

        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                } else {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FloatingBallService::class.java),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("开启悬浮球")
        }

        OutlinedButton(
            onClick = {
                // 关闭悬浮球，并释放投屏 projection（停掉截屏服务）
                context.stopService(Intent(context, FloatingBallService::class.java))
                context.stopService(
                    Intent(context, app.sift.capture.service.ScreenCaptureService::class.java),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("关闭悬浮球")
        }

        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("授予使用情况访问权限（统计刷视频时长）")
        }

        Button(onClick = onOpenNotes, modifier = Modifier.fillMaxWidth()) {
            Text("查看我的知识笔记")
        }

        OutlinedButton(onClick = onOpenDashboard, modifier = Modifier.fillMaxWidth()) {
            Text("今日数据（诚实仪表盘）")
        }
    }
}
