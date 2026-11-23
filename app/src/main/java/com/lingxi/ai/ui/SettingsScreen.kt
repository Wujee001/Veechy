@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingxi.ai.BuildConfig
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.pet.PetPrefs
import com.lingxi.ai.pet.PetService
import com.lingxi.ai.data.Provider
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.LineSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft
import kotlin.math.roundToInt

internal val Presets = listOf(
    "七牛云" to "https://api.qnaigc.com",
    "千问兼容" to "https://maas.qianwenaiapi.com/compatible-mode/v1",
    "DeepSeek" to "https://api.deepseek.com",
    "OpenAI" to "https://api.openai.com"
)

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenKnowledge: () -> Unit = {},
    onOpenTimetable: () -> Unit = {},
    onOpenProviders: () -> Unit = {}
) {
    var showReset by remember { mutableStateOf(false) }

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
            SectionTitle("朗读")
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
            SectionTitle("课表")
            SoftCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenTimetable)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = Purple, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("上课时间", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            if (vm.courses.isEmpty()) "导入课表，聊天时按时间提醒"
                            else "${vm.courses.size} 节课 · 已按时间生效",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = InkSoft, modifier = Modifier.size(20.dp))
                }
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
            SectionTitle("天气")
            SoftCard {
                val (locOk, askLoc) = rememberPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                var pendingWeather by remember { mutableStateOf(false) }

                LaunchedEffect(locOk) {
                    if (locOk && pendingWeather) {
                        vm.updateWeatherEnabled(true)
                        pendingWeather = false
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("天气", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "按定位查实时气温与降水，Open-Meteo免密钥",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = vm.weatherEnabled,
                        onCheckedChange = { on ->
                            when {
                                !on -> vm.updateWeatherEnabled(false)
                                locOk -> vm.updateWeatherEnabled(true)
                                else -> {
                                    pendingWeather = true
                                    askLoc()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }

                if (vm.weatherEnabled) {
                    Text(
                        "状态：" + vm.weatherStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                    )
                }

            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("夜间模式")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("夜间模式", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "仅整体调暗",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = vm.nightMode,
                        onCheckedChange = vm::updateNightMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("桌面小 Veechy")
            SoftCard {
                val ctx = LocalContext.current
                var petOn by remember { mutableStateOf(PetPrefs.enabled(ctx)) }
                var petMsg by remember { mutableStateOf(PetPrefs.messagesEnabled(ctx)) }

                val notifyLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {}

                val overlayLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) {
                    if (Settings.canDrawOverlays(ctx)) {
                        PetPrefs.setEnabled(ctx, true)
                        petOn = true
                        startPet(ctx)
                        askNotify(ctx, notifyLauncher::launch)
                    } else {
                        petOn = false
                        vm.say("没有「显示在其他应用上层」权限，桌宠无法出现")
                    }
                }

                fun enablePet() {
                    PetPrefs.setEnabled(ctx, true)
                    petOn = true
                    startPet(ctx)
                    askNotify(ctx, notifyLauncher::launch)
                }

                // 部分系统在授予悬浮窗权限时会重启 App：回到设置页后补上启动
                LaunchedEffect(Unit) {
                    if (PetPrefs.takePending(ctx) && Settings.canDrawOverlays(ctx)) {
                        enablePet()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("放在桌面上", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "小 Veechy 悬浮在桌面，可拖动、点一下会互动",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = petOn,
                        onCheckedChange = { on ->
                            if (on) {
                                if (Settings.canDrawOverlays(ctx)) enablePet()
                                else runCatching {
                                    PetPrefs.markPending(ctx)
                                    overlayLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${ctx.packageName}")
                                        )
                                    )
                                }.onFailure {
                                    vm.say("请在系统设置里允许「显示在其他应用上层」")
                                }
                            } else {
                                PetPrefs.setEnabled(ctx, false)
                                petOn = false
                                runCatching { ctx.stopService(Intent(ctx, PetService::class.java)) }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("定时冒一句话", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "隔 1–3 小时随机说一句，气泡里先出现，打开 App 后会留在当前对话",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = petMsg,
                        onCheckedChange = { on ->
                            PetPrefs.setMessagesEnabled(ctx, on)
                            petMsg = on
                            if (petOn) {
                                runCatching {
                                    ctx.startService(
                                        Intent(ctx, PetService::class.java)
                                            .setAction(PetService.ACTION_RELOAD)
                                    )
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }

                if (petOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "小提示：点一下会说话，双击打开 App，拖动可换位置。部分系统需再允许「后台弹出界面 / 自启动」，否则锁屏或清理后台后桌宠会消失。",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSoft
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("API 供应商")
            SoftCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenProviders)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Key, null, tint = Purple, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("供应商与模型", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "${vm.providers.size} 个供应商 · ${vm.activeModelLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = InkSoft, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("反馈")
            SoftCard {
                Text(
                    "如有反馈和建议可以邮箱发送至 001@jmu.edu.cn",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("关于")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientDot(28)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Veechy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Text(
                            "本地运行 · API Key 只存在本机",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "朗读使用系统自带的 TTS 引擎；用户填写的资料都只保存在本机，API Key 不会上传到任何第三方。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
            }

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "重新填写资料",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showReset = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }

    if (showReset) {
        ResetProfileDialog(vm = vm, onDismiss = { showReset = false })
    }

}

// ---------------------------------------------------------------- 供应商卡片

@Composable
internal fun FieldLabel(text: String) {
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
private fun ResetProfileDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    val count = vm.conversations.size

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("重新填写资料") },
            text = {
                Text(
                    "更改性别会清空所有对话记录，且无法恢复。\n\n" +
                        "课表、知识库、API 供应商不受影响。\n\n" +
                        "当前共 $count 个会话。"
                )
            },
            confirmButton = { TextButton({ step = 2 }) { Text("继续") } },
            dismissButton = { TextButton(onDismiss) { Text("取消") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("再确认一次") },
            text = { Text("$count 个会话将被永久删除，无法找回。确认继续？") },
            confirmButton = {
                TextButton({
                    vm.resetProfile()
                    onDismiss()
                }) { Text("清空并继续", color = Color(0xFFE5484D)) }
            },
            dismissButton = { TextButton(onDismiss) { Text("取消") } }
        )
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple.copy(alpha = 0.55f),
    unfocusedBorderColor = LineSoft,
    focusedContainerColor = Color(0xFFFBFAFF),
    unfocusedContainerColor = Color(0xFFF8F7FD),
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    cursorColor = Purple
)


private fun startPet(ctx: android.content.Context) {
    runCatching {
        ContextCompat.startForegroundService(ctx, Intent(ctx, PetService::class.java))
    }
}

private fun askNotify(ctx: android.content.Context, launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
