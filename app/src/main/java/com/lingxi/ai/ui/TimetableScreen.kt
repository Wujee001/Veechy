@file:OptIn(ExperimentalMaterial3Api::class)

package com.lingxi.ai.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.data.Course
import com.lingxi.ai.data.Timetable
import com.lingxi.ai.media.MediaUtil
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft

@Composable
fun TimetableScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var imagePath by remember { mutableStateOf<String?>(null) }
    var rules by remember { mutableStateOf(vm.timetableRules) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imagePath = MediaUtil.saveFromUri(ctx, uri)?.absolutePath
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("课表", color = Ink) },
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

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("在对话中启用课表", style = MaterialTheme.typography.bodyMedium, color = Ink)
                        Text(
                            "关掉后只注入当前时间，不再带整周课表，可省 token",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft
                        )
                    }
                    Switch(
                        checked = vm.timetableEnabled,
                        onCheckedChange = vm::updateTimetableEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Purple
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("导入课表", style = MaterialTheme.typography.titleSmall, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "上传课表截图，再把学校作息写清楚（每节课时长、各节起止时间、大课间），模型会把节次换算成具体时间。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rules,
                    onValueChange = {
                        rules = it
                        vm.updateTimetableRules(it)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 220.dp),
                    minLines = 3,
                    maxLines = 8,
                    placeholder = {
                        Text(
                            "例如：每节课 45 分钟，第 1 节 08:00–08:45，第 2 节 08:50–09:35……",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple.copy(alpha = 0.55f),
                        unfocusedBorderColor = Color(0xFFEDEAF7),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF8F7FD)
                    )
                )

                Spacer(Modifier.height(10.dp))

                imagePath?.let { path ->
                    val bmp = remember(path) { MediaUtil.loadScaled(path, 600) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Image, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("选择截图", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = { vm.parseTimetable(imagePath) },
                        enabled = !vm.ttBusy,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Purple,
                            contentColor = Color.White
                        )
                    ) {
                        if (vm.ttBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("解析并保存", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("已保存", "${vm.courses.size} 节")

            if (vm.courses.isEmpty()) {
                SoftCard {
                    Text(
                        "还没有课表。导入后，我会在合适的时候提醒你上课。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSoft
                    )
                }
            } else {
                vm.courses.groupBy { it.weekday }.toSortedMap().forEach { (day, list) ->
                    Text(
                        Timetable.weekdayName(day),
                        style = MaterialTheme.typography.titleSmall,
                        color = Purple,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 4.dp)
                    )
                    SoftCard {
                        list.forEachIndexed { i, c ->
                            CourseRow(c)
                            if (i != list.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(Color(0xFFEDEAF7))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { vm.clearTimetable() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33E5484D))
                ) { Text("清空课表", color = Color(0xFFE5484D), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun CourseRow(c: Course) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(84.dp)) {
            Text(
                c.start,
                style = MaterialTheme.typography.bodySmall,
                color = Ink
            )
            Text(c.end, style = MaterialTheme.typography.labelSmall, color = InkSoft)
        }
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = listOf(c.place, c.teacher, c.weeks).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Badge("${c.weeks}".ifBlank { "每周" }, Blue)
    }
}