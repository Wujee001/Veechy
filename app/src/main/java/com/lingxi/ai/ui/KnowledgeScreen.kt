@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.kb.IsbnLookup
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KnowledgeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var isbn by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importDocument(uri)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("知识库", color = Ink) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = Ink)
                    }
                },
                actions = {
                    vm.kbBusy?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Purple
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = InkSoft)
                            Spacer(Modifier.width(12.dp))
                        }
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
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("对话时检索知识库", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "回答前先从已保存的资料里找相关段落",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = vm.kbEnabled,
                        onCheckedChange = vm::updateKbEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------------- 上传文件
            SoftCard {
                Text("添加资料", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "支持 PDF、Word(docx)、PPT(pptx)、TXT/Markdown。文件内容提取后存进本机，" +
                        "不传云端，只有你主动删除才会消失。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { picker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------------- ISBN
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MenuBook, null, tint = Purple, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("按 ISBN 添加书目", style = MaterialTheme.typography.titleSmall, color = Ink)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "查询公开书目信息（书名、作者、出版社、简介、分类）并存入知识库。" +
                        "只取书目元数据，不包含书籍正文。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("9787115428028", style = MaterialTheme.typography.bodySmall) },
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color(0xFFEDEAF7),
                            focusedContainerColor = Color(0xFFFBFAFF),
                            unfocusedContainerColor = Color(0xFFF8F7FD)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { vm.lookupIsbn(isbn) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleSoft, contentColor = Purple)
                    ) { Text("查询") }
                }

                vm.bookPreview?.let { b ->
                    var eTitle by remember(b) { mutableStateOf(b.title) }
                    var eAuthors by remember(b) { mutableStateOf(b.authors) }
                    var ePublisher by remember(b) { mutableStateOf(b.publisher) }
                    var eYear by remember(b) { mutableStateOf(b.year) }
                    var eDesc by remember(b) { mutableStateOf(b.description) }

                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF7F5FF))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(
                                    if (b.origin == "model") "模型推断" else "在线书目",
                                    if (b.origin == "model") Color(0xFFE5484D) else Blue
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (b.origin == "model") "可能有误，请核对后再入库" else "来自公开书目接口",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkSoft
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            KbField("书名", eTitle) { eTitle = it }
                            Spacer(Modifier.height(6.dp))
                            KbField("作者", eAuthors) { eAuthors = it }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.weight(1f)) {
                                    KbField("出版社", ePublisher) { ePublisher = it }
                                }
                                Box(Modifier.weight(0.6f)) {
                                    KbField("出版年", eYear) { eYear = it }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            KbField("简介", eDesc, singleLine = false) { eDesc = it }

                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        vm.saveBookToKb(
                                            b.copy(
                                                title = eTitle.trim(),
                                                authors = eAuthors.trim(),
                                                publisher = ePublisher.trim(),
                                                year = eYear.trim(),
                                                description = eDesc.trim()
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Purple,
                                        contentColor = Color.White
                                    )
                                ) { Text("存入知识库", style = MaterialTheme.typography.labelSmall) }
                                Button(
                                    onClick = { vm.clearBookPreview() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = InkSoft
                                    )
                                ) { Text("关闭", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("已保存", "${vm.kbDocs.size} 份")

            if (vm.kbDocs.isEmpty()) {
                SoftCard {
                    Text(
                        "还没有资料。上传课件、论文或整本书，之后提问会自动引用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
            } else {
                vm.kbDocs.forEach { d ->
                    SoftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    d.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ink,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(d.kind.uppercase(), Blue)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${d.chars} 字 · ${d.chunkCount} 段 · ${formatDate(d.addedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkSoft
                                    )
                                }
                            }
                            IconButton({ vm.removeKnowledge(d.id) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    "删除",
                                    tint = InkSoft.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun KbField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        textStyle = MaterialTheme.typography.bodySmall,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple.copy(alpha = 0.55f),
            unfocusedBorderColor = Color(0xFFEDEAF7),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink
        )
    )
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
