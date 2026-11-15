package com.lingxi.ai.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 左侧边缘右滑返回。只在屏幕左边 28dp 的窄条上响应，
 * 不影响页面内部滑杆、列表等水平手势。
 */
@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    var dx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(dx.roundToInt(), 0) }
        ) {
            content()
        }

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dx > threshold) {
                                dx = 0f
                                onBack()
                            } else {
                                dx = 0f
                            }
                        },
                        onDragCancel = { dx = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 0 || dx > 0f) {
                                dx = (dx + dragAmount).coerceIn(0f, 320f)
                                change.consume()
                            }
                        }
                    )
                }
        )
    }
}
