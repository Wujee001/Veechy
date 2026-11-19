@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.data.JournalEntry
import com.lingxi.ai.media.MediaUtil
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.LineSoft
import com.lingxi.ai.ui.theme.Purple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(vm: MainViewModel) {
    var editing by remember { mutableStateOf<JournalEntry?>(null) }

    if (editing != null) {
        JournalEditor(
            vm = vm,
            entry = editing!!,
            onClose = { editing = null },
            onDeleted = { editing = null }
        )
    } else {
        JournalList(vm = vm, onOpen = { editing = it })
    }
}

@Composable
private fun JournalList(vm: MainViewModel, onOpen: (JournalEntry) -> Unit) {
    var preview by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("手帐", color = Ink) },
                actions = {
                    IconButton({
                        onOpen(JournalEntry(title = "", body = ""))
                    }) {
                        Icon(Icons.Rounded.NoteAdd, "新建", tint = Purple)
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
            if (vm.journalEntries.isEmpty()) {
                Spacer(Modifier.height(80.dp))
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GradientDot(54)
                    Spacer(Modifier.height(16.dp))
                    Text("还没有内容", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点右上角写第一条，支持 Markdown 和图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
            } else {
                vm.journalEntries.forEach { e ->
                    SoftCard {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(e) }) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.title.ifBlank { "无标题" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(e.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSoft
                                )
                                if (e.body.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        e.body.replace("\n", " ").take(70),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSoft,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (e.images.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                e.images.take(4).forEach { p ->
                                    val bmp = rememberBmpSmall(p)
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { preview = p }
                                        )
                                    }
                                }
                                if (e.images.size > 4) {
                                    Text(
                                        "+${e.images.size - 4}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSoft,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    preview?.let { ImageViewer(it) { preview = null } }
}

@Composable
private fun JournalEditor(
    vm: MainViewModel,
    entry: JournalEntry,
    onClose: () -> Unit,
    onDeleted: () -> Unit
) {
    val ctx = LocalContext.current
    val isNew = entry.title.isBlank() && entry.body.isBlank() && entry.images.isEmpty()

    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var bodyValue by remember(entry.id) { mutableStateOf(TextFieldValue(entry.body)) }
    var images by remember(entry.id) { mutableStateOf(entry.images) }
    var previewing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val saved = uris.mapNotNull { MediaUtil.saveFromUri(ctx, it)?.absolutePath }
            images = images + saved
        }
    }

    fun surround(prefix: String, suffix: String = prefix) {
        val text = bodyValue.text
        val sel = bodyValue.selection
        val a = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val b = maxOf(sel.start, sel.end).coerceIn(0, text.length)
        val chosen = text.substring(a, b)
        val insert = prefix + chosen + suffix
        bodyValue = TextFieldValue(text.replaceRange(a, b, insert), TextRange(a + insert.length))
    }

    fun linePrefix(prefix: String) {
        val text = bodyValue.text
        val sel = bodyValue.selection
        val at = minOf(sel.start, sel.end).coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        bodyValue = TextFieldValue(
            text.substring(0, lineStart) + prefix + text.substring(lineStart),
            TextRange(at + prefix.length)
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "写手帐" else "编辑手帐", color = Ink) },
                navigationIcon = {
                    IconButton(onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink)
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton({
                            vm.deleteJournal(entry.id)
                            onDeleted()
                        }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = InkSoft)
                        }
                    }
                    TextButton({
                        vm.saveJournal(
                            entry.copy(
                                title = title.trim(),
                                body = bodyValue.text,
                                images = images
                            )
                        )
                        onClose()
                    }) { Text("保存", color = Purple) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .padding(horizontal = 14.dp)
                .padding(bottom = 12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("标题", style = MaterialTheme.typography.bodySmall) },
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (previewing) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (bodyValue.text.isBlank()) {
                        Text("（没有内容）", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    } else {
                        MarkdownText(bodyValue.text, color = Ink)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MdButton("加粗") { surround("**") }
                    MdButton("斜体") { surround("*") }
                    MdButton("标题") { linePrefix("## ") }
                    MdButton("引用") { linePrefix("> ") }
                    MdButton("列表") { linePrefix("- ") }
                    MdButton("代码") { surround("`") }
                }

                OutlinedTextField(
                    value = bodyValue,
                    onValueChange = { bodyValue = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = {
                        Text(
                            "支持 Markdown：**加粗**、> 引用、- 列表、`代码`、| 表格 |",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("加图片", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { previewing = !previewing },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (previewing) "继续编辑" else "预览",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (images.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    images.forEach { path ->
                        Box {
                            val bmp = rememberBmpSmall(path)
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { preview = path }
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { images = images - path },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    "移除",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    preview?.let { ImageViewer(it) { preview = null } }
}

@Composable
private fun MdButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1EDFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Purple)
    }
}

/** 全屏查看，点击任意处关闭。 */
@Composable
private fun ImageViewer(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val bmp = remember(path) { MediaUtil.loadScaled(path, 2048) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            } else {
                Text("图片已丢失", color = Color.White)
            }
        }
    }
}

@Composable
private fun rememberBmpSmall(path: String) = remember(path) {
    runCatching { MediaUtil.loadScaled(path, 360) }.getOrNull()
}