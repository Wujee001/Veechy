@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.audio.SpeechInput
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleDeep
import com.lingxi.ai.ui.theme.PurpleSoft

@Composable
fun VoiceScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val (granted, askMic) = rememberPermission(MicPermission)
    val speech = remember { SpeechInput(context) }
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        speech.autoRestart = true
        speech.onFinal = { heard -> vm.send(heard, speak = true) }
    }
    DisposableEffect(Unit) {
        onDispose { speech.destroy() }
    }
    LaunchedEffect(granted, enabled) {
        if (granted && enabled) speech.start() else speech.stop()
    }

    val lastUser = vm.messages.lastOrNull { it.role == "user" }?.text.orEmpty()
    val lastAi = vm.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
    val liveText = if (speech.listening && speech.partial.isNotBlank()) speech.partial else lastUser

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
                        enabled && speech.listening -> "在听… 说完自动发送"
                        enabled -> "已暂停"
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

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            WaveOrb(active = enabled && speech.listening, level = speech.level)

            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    vm.streaming -> "正在想…"
                    vm.tts.speaking -> "正在说…"
                    enabled && speech.listening -> "请讲"
                    else -> "待机"
                },
                style = MaterialTheme.typography.titleSmall,
                color = PurpleDeep
            )

            Spacer(Modifier.height(20.dp))

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

            speech.errorText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = InkSoft.copy(alpha = 0.8f))
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
                        if (enabled) Brush.linearGradient(listOf(Color(0xFFE5484D), Color(0xFFFF7A7E)))
                        else Brush.linearGradient(listOf(Purple, Blue))
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (!granted) {
                            askMic()
                        } else {
                            enabled = !enabled
                        }
                    },
                    modifier = Modifier.size(78.dp)
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "麦克风",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                if (enabled) "点击结束" else "点击开始说话",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}
