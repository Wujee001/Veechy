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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.camera.CameraController
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import kotlinx.coroutines.launch

@Composable
fun VideoScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val (camGranted, askCam) = rememberPermission(CamPermission)
    val camera = remember { CameraController(context, lifecycleOwner) }
    var useFront by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) { if (!camGranted) askCam() }
    LaunchedEffect(camGranted, useFront) { if (camGranted) camera.bind(previewView, useFront) }
    DisposableEffect(Unit) {
        onDispose { camera.unbind() }
    }

    val lastAi = vm.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("视觉", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    "点「看一眼」，把当前画面拍下来发过去",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
            }
            IconButton(onOpenSettings) {
                Icon(Icons.Rounded.Settings, "设置", tint = InkSoft)
            }
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
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("需要相机权限", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = askCam) { Text("授予权限") }
                }
            }

            if (lastAi.isNotBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(14.dp)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(Purple),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!camGranted) {
                                askCam()
                            } else if (!vm.streaming) {
                                scope.launch {
                                    capturing = true
                                    val f = camera.snap()
                                    capturing = false
                                    if (f != null) vm.send("", listOf(f.absolutePath))
                                }
                            }
                        },
                        modifier = Modifier.size(70.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = "看一眼",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Text("看一眼", style = MaterialTheme.typography.labelSmall, color = InkSoft)
            }
        }
    }
}