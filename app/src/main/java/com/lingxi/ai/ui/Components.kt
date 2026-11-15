package com.lingxi.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lingxi.ai.ui.theme.Blue
import com.lingxi.ai.ui.theme.CardBg
import com.lingxi.ai.ui.theme.Ink
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Purple.copy(alpha = 0.16f),
                ambientColor = Purple.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = InkSoft,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = InkSoft.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun GradientDot(size: Int = 34) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Purple, Blue))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
}

/** 语音动效球：随输入音量缩放。 */
@Composable
fun WaveOrb(active: Boolean, level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val scale = if (active) (0.92f + level * 0.30f) * pulse else 1f

    Box(modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(scale * 1.10f)
                .background(
                    Brush.radialGradient(
                        listOf(Purple.copy(alpha = 0.26f), Blue.copy(alpha = 0.06f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(148.dp)
                .scale(scale)
                .background(Brush.linearGradient(listOf(Purple, Purple.copy(alpha = 0.85f), Blue)), CircleShape)
        )
        Box(
            Modifier
                .size(148.dp)
                .scale(scale)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(52.dp)
        )
    }
}

/** 运行时权限：返回 (是否已授权, 请求函数)。 */
@Composable
fun rememberPermission(permission: String): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    return granted to { if (!granted) launcher.launch(permission) }
}

@Composable
fun EmptyHint(icon: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

val MicPermission: String = Manifest.permission.RECORD_AUDIO
val CamPermission: String = Manifest.permission.CAMERA
