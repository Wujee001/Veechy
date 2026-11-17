@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.data.ChatMessage
import com.lingxi.ai.data.InnerVoice
import com.lingxi.ai.media.MediaUtil
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.LineSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleDeep
import com.lingxi.ai.ui.theme.PurpleSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(vm: MainViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var pendingImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val saved = uris.mapNotNull { MediaUtil.saveFromUri(context, it)?.absolutePath }
            pendingImages = (pendingImages + saved).take(9)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ChatTopBar(
            vm = vm,
            onOpenSettings = onOpenSettings,
            onHistory = { showHistory = true },
            onNew = { vm.createConversation() }
        )
        HorizontalDivider(color = LineSoft.copy(alpha = 0.6f))

        val listState = rememberLazyListState()
        val lastLen = vm.messages.lastOrNull()?.text?.length ?: 0
        LaunchedEffect(vm.current?.id, vm.messages.size, lastLen) {
            if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
        }

        if (vm.messages.isEmpty()) {
            EmptyChat(onPickSuggest = { vm.send(it) }, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.messages, key = { it.id }) { m -> MessageBubble(m) }
            }
        }

        InputBar(
            vm = vm,
            pendingImages = pendingImages,
            onPickImage = { picker.launch("image/*") },
            onRemoveImage = { path -> pendingImages = pendingImages - path },
            onSent = { pendingImages = emptyList() }
        )
    }

    if (showHistory) {
        HistorySheet(
            vm = vm,
            onClose = { showHistory = false },
            onOpen = { id -> vm.openConversation(id); showHistory = false },
            onNew = { vm.createConversation(); showHistory = false }
        )
    }
}

// ---------------------------------------------------------------- 顶栏

@Composable
private fun ChatTopBar(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onHistory: () -> Unit,
    onNew: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Purple, Blue))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.current?.title ?: "Wujee's AI", style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 1)
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menu = true }
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        vm.activeModelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp)
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = InkSoft, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val enabled = vm.providers.filter { it.enabled }
                    if (enabled.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("先去设置添加供应商") },
                            onClick = { menu = false; onOpenSettings() }
                        )
                    } else {
                        enabled.forEach { p ->
                            if (p.models.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("${p.name} · 未同步模型", maxLines = 1) },
                                    onClick = { menu = false }
                                )
                            } else {
                                p.models.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(p.name, style = MaterialTheme.typography.labelSmall, color = InkSoft)
                                            }
                                        },
                                        onClick = {
                                            vm.selectModel(p.id, m.id)
                                            menu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        IconButton(onNew) { Icon(Icons.Rounded.AddCircleOutline, "新对话", tint = InkSoft) }
        IconButton(onHistory) { Icon(Icons.Rounded.History, "历史对话", tint = InkSoft) }
        IconButton(onOpenSettings) { Icon(Icons.Rounded.Settings, "设置", tint = InkSoft) }
    }
}

// ---------------------------------------------------------------- 空状态

@Composable
private fun EmptyChat(onPickSuggest: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GradientDot(size = 54)
        Spacer(Modifier.height(16.dp))
        Text("有什么可以帮你？", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "文字、语音、拍照都能聊。先在右上角设置里填入 API。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        Spacer(Modifier.height(22.dp))
        listOf(
            "用三句话解释一下量子纠缠",
            "帮我把这段话改得更简洁",
            "写一个 Python 快速排序"
        ).forEach { s ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable { onPickSuggest(s) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(s, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}

// ---------------------------------------------------------------- 气泡

@Composable
fun MessageBubble(m: ChatMessage) {
    val isUser = m.role == "user"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Purple, Blue))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            Modifier
                .widthIn(max = 330.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(Purple, PurpleDeep))
                    else Brush.linearGradient(listOf(Color.White, Color.White))
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
            Column {
            m.images.forEach { path ->
                val bmp = rememberBmp(path)
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            val (thought, rawBody) = remember(m.text) { InnerVoice.split(m.text) }
            if (thought != null) {
                Text(
                    "（$thought）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) Color.White.copy(alpha = 0.8f) else InkSoft,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(6.dp))
            }

            val body = rawBody.ifBlank {
                if (m.role == "assistant") "思考中…" else ""
            }
            if (body.isNotBlank()) {
                val textColor = when {
                    m.error -> Color(0xFFE5484D)
                    isUser -> Color.White
                    else -> Ink
                }
                if (isUser) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = textColor)
                } else {
                    MarkdownText(
                        text = body,
                        color = textColor,
                        baseStyle = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            }
            }
        }
    }
}

@Composable
private fun rememberBmp(path: String): Bitmap? = remember(path) {
    runCatching { MediaUtil.loadScaled(path, 720) }.getOrNull()
}

// ---------------------------------------------------------------- 输入栏

@Composable
private fun InputBar(
    vm: MainViewModel,
    pendingImages: List<String>,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSent: () -> Unit
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val (micGranted, askMic) = rememberPermission(MicPermission)
    val speech = remember { com.lingxi.ai.audio.SpeechInput(ctx) }

    LaunchedEffect(Unit) {
        speech.autoRestart = false
        speech.onFinal = { heard ->
            text = if (text.isBlank()) heard else "$text $heard"
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { speech.destroy() }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (pendingImages.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingImages.forEach { path ->
                    Box {
                        val bmp = rememberBmp(path)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onRemoveImage(path) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                "移除",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onPickImage) {
                Icon(Icons.Rounded.Image, "图片", tint = InkSoft)
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 140.dp),
                placeholder = {
                    Text(
                        if (speech.listening) "正在听…" else "发消息…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft.copy(alpha = 0.7f)
                    )
                },
                shape = RoundedCornerShape(23.dp),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple.copy(alpha = 0.45f),
                    unfocusedBorderColor = LineSoft,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color(0xFFF8F7FD)
                )
            )

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = {
                    if (!micGranted) askMic()
                    else if (speech.listening) speech.stop() else speech.start()
                }
            ) {
                Icon(
                    Icons.Rounded.Mic,
                    "语音输入",
                    tint = if (speech.listening) Purple else InkSoft
                )
            }

            FilledIconButton(
                onClick = {
                    if (vm.streaming) {
                        vm.stopGenerating()
                    } else {
                        val t = text.trim()
                        if (t.isNotBlank() || pendingImages.isNotEmpty()) {
                            vm.send(t, pendingImages)
                            text = ""
                            onSent()
                        }
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (vm.streaming) Color(0xFFE5484D) else Purple,
                    contentColor = Color.White
                ),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    if (vm.streaming) Icons.Rounded.Stop else Icons.Rounded.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 历史

@Composable
private fun HistorySheet(
    vm: MainViewModel,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color.White,
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("历史对话", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                TextButton(onNew) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新对话")
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(vm.conversations, key = { it.id }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (c.id == vm.current?.id) PurpleSoft else Color.Transparent)
                            .clickable { onOpen(c.id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${c.messages.size} 条 · ${formatTime(c.updatedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkSoft
                            )
                        }
                        IconButton({ vm.deleteConversation(c.id) }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = InkSoft.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
