package app.sift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.sift.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {
    var baseUrl by mutableStateOf("")
        private set
    var model by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var saved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            baseUrl = settings.getBaseUrl()
            model = settings.getModel()
            apiKey = settings.getApiKey().orEmpty()
        }
    }

    fun onBaseUrl(v: String) { baseUrl = v; saved = false }
    fun onModel(v: String) { model = v; saved = false }
    fun onApiKey(v: String) { apiKey = v; saved = false }

    fun save() {
        viewModelScope.launch {
            settings.save("openai-compatible", baseUrl.trim(), model.trim(), apiKey.trim())
            saved = true
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("API 设置", style = MaterialTheme.typography.headlineSmall)
        Text(
            "自带 Key。任何 OpenAI 兼容服务都行（OpenAI / DeepSeek / Moonshot / 本地 Ollama / 中转），" +
                "靠 Base URL 区分。模型需支持图片输入（多模态）。",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = vm.baseUrl,
            onValueChange = vm::onBaseUrl,
            label = { Text("Base URL") },
            placeholder = { Text("https://api.openai.com/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.model,
            onValueChange = vm::onModel,
            label = { Text("模型名") },
            placeholder = { Text("gpt-4o-mini") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = vm::onApiKey,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.saved) "已保存 ✓" else "保存")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}
