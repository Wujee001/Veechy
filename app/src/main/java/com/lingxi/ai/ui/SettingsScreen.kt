@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.data.Provider
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.LineSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft
import kotlin.math.roundToInt

private val Presets = listOf(
    "七牛云" to "https://api.qnaigc.com",
    "千问兼容" to "https://maas.qianwenaiapi.com/compatible-mode/v1",
    "DeepSeek" to "https://api.deepseek.com",
    "OpenAI" to "https://api.openai.com"
)

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenKnowledge: () -> Unit = {}) {
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Provider?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置", color = Ink) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 32.dp)
        ) {
            SectionTitle("API 供应商", "${vm.providers.size} 个")

            vm.providers.forEach { p ->
                ProviderCard(
                    vm = vm,
                    provider = p,
                    onDelete = { pendingDelete = p }
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Purple.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Rounded.Add, null, tint = Purple, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加供应商", color = Purple)
            }

            if (vm.busyHint != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    vm.busyHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("对话")
            SoftCard {
                Text("系统提示词", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = vm.systemPrompt,
                    onValueChange = vm::updateSystemPrompt,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )

                Spacer(Modifier.height(14.dp))
                SliderRow(
                    label = "温度",
                    value = vm.temperature.toDouble(),
                    range = 0.0..1.5,
                    steps = 14,
                    display = String.format("%.1f", vm.temperature)
                ) { vm.updateTemperature(it.toFloat()) }

                SliderRow(
                    label = "携带历史轮数",
                    value = vm.contextTurns.toDouble(),
                    range = 0.0..30.0,
                    steps = 29,
                    display = "${vm.contextTurns} 轮"
                ) { vm.updateContextTurns(it.roundToInt()) }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("语音")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动朗读回复", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "语音 / 视觉对话中总是朗读",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = vm.autoSpeak,
                        onCheckedChange = vm::updateAutoSpeak,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                SliderRow(
                    label = "语速",
                    value = vm.tts.rate.toDouble(),
                    range = 0.6..1.8,
                    steps = 11,
                    display = String.format("%.1fx", vm.tts.rate)
                ) { vm.updateSpeechRate(it.toFloat()) }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("知识库")
            SoftCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenKnowledge)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.MenuBook, null, tint = Purple, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("管理知识库", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "${vm.kbDocs.size} 份资料 · 长期保存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = InkSoft, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("关于")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientDot(28)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Wujee's AI 1.3.0", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text(
                            "本地运行 · API Key 只存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "语音输入使用系统 SpeechRecognizer，朗读使用系统 TTS。若提示识别不可用，请在系统设置中安装/启用语音服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }
        }
    }

    if (showAdd) {
        AddProviderDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, base, key ->
                vm.addProvider(name, base, key)
                showAdd = false
            }
        )
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除供应商") },
            text = { Text("确定删除「${p.name}」及其模型列表？") },
            confirmButton = {
                TextButton({
                    vm.removeProvider(p.id)
                    pendingDelete = null
                }) { Text("删除", color = Color(0xFFE5484D)) }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text("取消") } }
        )
    }
}

// ---------------------------------------------------------------- 供应商卡片

@Composable
private fun ProviderCard(vm: MainViewModel, provider: Provider, onDelete: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    var expanded by remember(provider.id) { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = provider.name,
                    onValueChange = { vm.updateProvider(provider.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = RoundedCornerShape(10.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = { vm.updateProvider(provider.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Purple
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            FieldLabel("API 密钥")
            OutlinedTextField(
                value = provider.apiKey,
                onValueChange = { vm.updateProvider(provider.copy(apiKey = it.trim())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("sk-...", style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ reveal = !reveal }) {
                        Icon(
                            if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "显示密钥",
                            tint = InkSoft,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors()
            )

            Spacer(Modifier.height(10.dp))
            FieldLabel("API 地址")
            OutlinedTextField(
                value = provider.baseUrl,
                onValueChange = { vm.updateProvider(provider.copy(baseUrl = it.trim())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://api.xxx.com/v1", style = MaterialTheme.typography.bodySmall) },
                shape = RoundedCornerShape(10.dp),
                colors = fieldColors()
            )

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8F7FD))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("联网搜索", style = MaterialTheme.typography.bodyMedium, color = Ink)
                    Text(
                        "请求里带 enable_search=true，由服务端联网检索（阿里云百炼 / 通义千问支持）",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft
                    )
                }
                Switch(
                    checked = provider.search,
                    onCheckedChange = { vm.updateProvider(provider.copy(search = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Purple
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { vm.testProvider(provider.id) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) { Text("检测", style = MaterialTheme.typography.labelSmall) }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = { vm.syncModels(provider.id) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleSoft, contentColor = Purple),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("同步模型", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.weight(1f))

                IconButton(onDelete) {
                    Icon(Icons.Rounded.Delete, "删除", tint = InkSoft.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            val currentModel = if (vm.activeProviderId == provider.id) vm.activeModelId else null
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "模型 · ${provider.models.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft
                    )
                    Text(
                        currentModel ?: "未选择（点右侧展开）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentModel != null) Purple else InkSoft.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (expanded) "收起" else "选择模型",
                    style = MaterialTheme.typography.labelSmall,
                    color = Purple
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Purple,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (expanded) {
                if (provider.models.isEmpty()) {
                    Text(
                        "还没有模型，点「同步模型」拉取",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft.copy(alpha = 0.75f)
                    )
                } else {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        singleLine = true,
                        placeholder = {
                            Text("搜索模型名", style = MaterialTheme.typography.bodySmall)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors()
                    )
                    provider.models
                        .filter { filter.isBlank() || it.id.contains(filter, ignoreCase = true) }
                        .forEach { m ->
                            val selected = vm.activeProviderId == provider.id && vm.activeModelId == m.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) PurpleSoft.copy(alpha = 0.65f) else Color.Transparent)
                                    .clickable { vm.selectModel(provider.id, m.id) }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { vm.selectModel(provider.id, m.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Purple, unselectedColor = LineSoft),
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    m.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) Ink else InkSoft,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (m.vision) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge("图", Blue)
                                }
                                if (m.audio) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge("音", Purple)
                                }
                            }
                        }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 添加对话框

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加供应商") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Presets.forEach { (n, url) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurpleSoft)
                                .clickable {
                                    name = n
                                    base = url
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(n, style = MaterialTheme.typography.labelSmall, color = Purple)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.xxx.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API 密钥") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (base.isNotBlank()) onConfirm(name.ifBlank { "自定义" }, base, key) }
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------- 小部件

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = InkSoft,
        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    steps: Int,
    display: String,
    onChange: (Double) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.labelSmall, color = Purple)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Purple,
                activeTrackColor = Purple,
                inactiveTrackColor = LineSoft
            )
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple.copy(alpha = 0.55f),
    unfocusedBorderColor = LineSoft,
    focusedContainerColor = Color(0xFFFBFAFF),
    unfocusedContainerColor = Color(0xFFF8F7FD),
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    cursorColor = Purple
)
