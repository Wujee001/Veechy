package com.lingxi.ai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingxi.ai.MainViewModel
import com.lingxi.ai.ui.theme.GradientBackground
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft

private enum class Screen { MAIN, SETTINGS, KNOWLEDGE }

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf(Screen.MAIN) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.status) {
        val s = vm.status
        if (s != null) {
            snackbar.showSnackbar(s)
            vm.consumeStatus()
        }
    }

    GradientBackground {
        when (screen) {
            Screen.MAIN -> Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = { AppBottomBar(tab) { tab = it } }
            ) { inner ->
                Box(Modifier.fillMaxSize().padding(inner)) {
                    when (tab) {
                        0 -> ChatScreen(vm, onOpenSettings = { screen = Screen.SETTINGS })
                        1 -> VoiceScreen(vm, onOpenSettings = { screen = Screen.SETTINGS })
                        else -> VideoScreen(vm, onOpenSettings = { screen = Screen.SETTINGS })
                    }
                }
            }

            Screen.SETTINGS -> SwipeBackContainer(onBack = { screen = Screen.MAIN }) {
                SettingsScreen(
                    vm = vm,
                    onBack = { screen = Screen.MAIN },
                    onOpenKnowledge = { screen = Screen.KNOWLEDGE }
                )
            }

            Screen.KNOWLEDGE -> SwipeBackContainer(onBack = { screen = Screen.SETTINGS }) {
                KnowledgeScreen(vm = vm, onBack = { screen = Screen.SETTINGS })
            }
        }
    }
}

@Composable
private fun AppBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "对话" to Icons.Rounded.ChatBubbleOutline,
        "语音" to Icons.Rounded.GraphicEq,
        "视觉" to Icons.Rounded.Videocam
    )
    NavigationBar(
        containerColor = Color.White.copy(alpha = 0.82f),
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(item.second, item.first) },
                label = { Text(item.first, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Purple,
                    selectedTextColor = Purple,
                    indicatorColor = PurpleSoft,
                    unselectedIconColor = InkSoft.copy(alpha = 0.75f),
                    unselectedTextColor = InkSoft.copy(alpha = 0.75f)
                )
            )
        }
    }
}
