@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.audio.SpeechInput
import com.lingxi.ai.camera.CameraController
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val (camGranted, askCam) = rememberPermission(CamPermission)
    val (micGranted, askMic) = rememberPermission(MicPermission)

    val speech = remember { SpeechInput(context) }
    val camera = remember { CameraController(context, lifecycleOwner) }
    var useFront by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        speech.autoRestart = true
        speech.onFinal = { heard ->
            if (!vm.streaming) {
                scope.launch {
                    capturing = true
                    val file = camera.snap()
                    capturing = false
                    vm.send(heard, file?.absolutePath, speak = true)
                }
            }
        }
    }

    LaunchedEffect(camGranted, micGranted) {
        if (!camGranted) askCam()
        if (!micGranted) askMic()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(camGranted, useFront) {
        if (camGranted) camera.bind(previewView, useFront)
    }

    LaunchedEffect(micGranted, enabled) {
        if (micGranted && enabled) speech.start() else speech.stop()
    }

    DisposableEffect(Unit) {
        onDispose {
            speech.destroy()
            camera.unbind()
        }
    }

    val lastAi = vm.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
    val lastUser = vm.messages.lastOrNull { it.role == "user" }?.text.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("视觉对话", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    "说一句话，自动拍下画面一起发送",
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

        if (!speech.available) {
            Text(
                "这台设备没有系统语音识别服务，视觉页只能用「看一眼」手动取帧；" +
                    "语音问答请到「语音」页切到「录音转写」。",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE5484D),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF12101F))
        ) {
            if (camGranted) {
                AndroidView(
                    factory = { previewView },
                    update = { },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.PhotoCamera, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("需要相机权限", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = askCam) { Text("授予权限") }
                }
            }

            // 字幕浮层
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lastAi.isNotBlank()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            lastAi,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val live = if (speech.listening && speech.partial.isNotBlank()) speech.partial else lastUser
                if (live.isNotBlank()) {
                    Text(
                        "你：$live",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (capturing || vm.streaming) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (capturing) "取帧…" else "思考…",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            IconButton(
                onClick = { useFront = !useFront },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Rounded.Cameraswitch, "切换镜头", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallCircle(icon = Icons.Rounded.PhotoCamera, label = "看一眼") {
                if (!vm.streaming) {
                    scope.launch {
                        capturing = true
                        val f = camera.snap()
                        capturing = false
                        vm.send("描述你看到的画面，用简短几句话。", f?.absolutePath, speak = true)
                    }
                }
            }
            Spacer(Modifier.width(28.dp))
            Box(
                Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) Brush.linearGradient(listOf(Color(0xFFE5484D), Color(0xFFFF7A7E)))
                        else Brush.linearGradient(listOf(Purple, Blue))
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        if (!micGranted) askMic() else enabled = !enabled
                    },
                    modifier = Modifier.size(70.dp)
                ) {
                    Icon(
                        if (enabled) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "麦克风",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.width(28.dp))
            SmallCircle(icon = Icons.Rounded.VolumeOff, label = "静音") { vm.tts.stop() }
        }
    }
}

@Composable
private fun SmallCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick, modifier = Modifier.size(52.dp)) {
                Icon(icon, label, tint = Purple, modifier = Modifier.size(22.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = InkSoft)
    }
}
