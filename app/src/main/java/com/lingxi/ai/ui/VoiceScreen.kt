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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.audio.SpeechInput
import com.lingxi.ai.audio.VoiceRecorder
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleDeep
import com.lingxi.ai.ui.theme.PurpleSoft

@Composable
fun VoiceScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val (granted, askMic) = rememberPermission(MicPermission)
    val speech = remember { SpeechInput(context) }
    val recorder = remember { VoiceRecorder(context) }

    // 0 = 系统识别，1 = 录音转写
    var mode by remember { mutableStateOf(if (speech.available) 0 else 1) }
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        speech.autoRestart = true
        speech.onFinal = { heard -> vm.send(heard, speak = true) }
    }
    DisposableEffect(Unit) {
        onDispose {
            speech.destroy()
            recorder.cancel()
        }
    }
    LaunchedEffect(granted, enabled, mode) {
        if (mode == 0 && granted && enabled) speech.start() else speech.stop()
        if (mode == 1 && enabled) enabled = false
    }

    val lastUser = vm.messages.lastOrNull { it.role == "user" }?.text.orEmpty()
    val lastAi = vm.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
    val liveText = if (speech.listening && speech.partial.isNotBlank()) speech.partial else lastUser

    val busy = vm.streaming || vm.sttBusy
    val level = if (mode == 0) speech.level else recorder.level
    val active = if (mode == 0) (enabled && speech.listening) else recorder.recording

    Column(Modifier.fillMaxSize()) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("语音对话", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    when {
                        !granted -> "需要麦克风权限"
                        busy -> if (vm.sttBusy) "正在转写…" else "正在想…"
                        mode == 0 && enabled && speech.listening -> "在听… 说完自动发送"
                        mode == 0 && enabled -> "已暂停"
                        mode == 1 && recorder.recording -> "录音中，再点一次结束"
                        else -> "点下方按钮开始"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            if (vm.tts.speaking) {
                IconButton({ vm.tts.stop() }) {
                    Icon(Icons.Rounded.VolumeOff, "停止朗读", tint = Purple)
                }
            }
            IconButton(onOpenSettings) {
                Icon(Icons.Rounded.Settings, "设置", tint = InkSoft)
            }
        }

        // 模式切换
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.75f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ModeChip("系统识别", mode == 0) { mode = 0 }
            ModeChip("录音转写", mode == 1) { mode = 1 }
        }

        if (!speech.available && mode == 0) {
            Text(
                "这台设备没有可用的系统语音识别服务，建议改用「录音转写」。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE5484D),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            WaveOrb(active = active, level = level)

            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    vm.sttBusy -> "转写中"
                    vm.streaming -> "正在想…"
                    vm.tts.speaking -> "正在说…"
                    active -> "请讲"
                    else -> "待机"
                },
                style = MaterialTheme.typography.titleSmall,
                color = PurpleDeep
            )

            Spacer(Modifier.height(18.dp))

            if (liveText.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(14.dp)
                ) {
                    Text(liveText, style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (lastAi.isNotBlank()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PurpleSoft.copy(alpha = 0.8f))
                        .padding(14.dp)
                ) {
                    Text(
                        lastAi,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val hint = speech.errorText
            if (mode == 0 && hint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft.copy(alpha = 0.8f)
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Brush.linearGradient(listOf(Color(0xFFE5484D), Color(0xFFFF7A7E)))
                        else Brush.linearGradient(listOf(Purple, Blue))
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (!granted) {
                            askMic()
                            return@IconButton
                        }
                        if (mode == 0) {
                            enabled = !enabled
                        } else {
                            if (recorder.recording) {
                                val file = recorder.stop()
                                if (file != null) vm.transcribeAndSend(file)
                            } else {
                                if (!recorder.start()) vm.say("无法启动录音，请检查麦克风权限")
                            }
                        }
                    },
                    modifier = Modifier.size(78.dp)
                ) {
                    Icon(
                        imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "麦克风",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                when {
                    mode == 1 && recorder.recording -> "点击结束并转写"
                    mode == 1 -> "点击录音"
                    enabled -> "点击结束"
                    else -> "点击开始说话"
                },
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Brush.linearGradient(listOf(Purple, Blue)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else InkSoft
        )
    }
}
