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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var profession by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }

    val ready = profession.isNotBlank() && gender.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        GradientDot(58)
        Spacer(Modifier.height(18.dp))
        Text("Wujee's AI", style = MaterialTheme.typography.headlineSmall, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "先回答两个问题，后面的对话会按你的情况来。",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )

        Spacer(Modifier.height(34.dp))

        Text("1 · 你的专业 / 职业", style = MaterialTheme.typography.titleSmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = profession,
            onValueChange = { profession = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text("例如：计算机科学与技术 / 临床医学", style = MaterialTheme.typography.bodySmall)
            },
            shape = RoundedCornerShape(12.dp),
            colors = darkField()
        )

        Spacer(Modifier.height(22.dp))

        Text("2 · 你的性别", style = MaterialTheme.typography.titleSmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = gender,
            onValueChange = { gender = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("女 / 男", style = MaterialTheme.typography.bodySmall) },
            shape = RoundedCornerShape(12.dp),
            colors = darkField()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("女", gender == "女") { gender = "女" }
            QuickChip("男", gender == "男") { gender = "男" }
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = { vm.completeOnboarding(profession, gender) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple,
                contentColor = Color.White,
                disabledContainerColor = Purple.copy(alpha = 0.25f),
                disabledContentColor = Color.White
            )
        ) { Text("开始") }

        Spacer(Modifier.height(14.dp))
        Text(
            "这些信息只保存在本机，不会上传。",
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Purple else Color.White.copy(alpha = 0.9f))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else InkSoft
        )
    }
}

@Composable
private fun darkField() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Purple.copy(alpha = 0.6f),
    unfocusedBorderColor = Color(0xFFEDEAF7),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White.copy(alpha = 0.85f),
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    cursorColor = Purple
)
