package com.lingxi.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingxi.ai.ui.theme.InkSoft
import com.lingxi.ai.ui.theme.Purple
import com.lingxi.ai.ui.theme.PurpleSoft

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val num: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
    data object Gap : MdBlock
}

/**
 * 轻量 Markdown 渲染：标题、列表、引用、代码块、表格、分割线、行内加粗/斜体/行内码/链接。
 * 不依赖第三方库，流式输出时容错（未闭合的标记按普通文本处理）。
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(text) { parseMarkdown(text) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    buildInline(block.text, color),
                    style = when (block.level) {
                        1 -> baseStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        2 -> baseStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        else -> baseStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    },
                    color = color
                )

                is MdBlock.Para -> Text(buildInline(block.text, color), style = baseStyle, color = color)

                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Text("•  ", style = baseStyle, color = color)
                    Text(buildInline(block.text, color), style = baseStyle, color = color)
                }

                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Text("${block.num}.  ", style = baseStyle, color = color)
                    Text(buildInline(block.text, color), style = baseStyle, color = color)
                }

                is MdBlock.Quote -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(buildInline(block.text, color), style = baseStyle, color = color.copy(alpha = 0.85f))
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (color == Color.White) Color(0xFFF4F2FB) else Color.Black.copy(alpha = 0.12f))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        block.code,
                        style = baseStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        ),
                        color = color
                    )
                }

                is MdBlock.Table -> MdTable(block.header, block.rows, color, baseStyle)

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(color.copy(alpha = 0.15f))
                )

                MdBlock.Gap -> Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun MdTable(
    header: List<String>,
    rows: List<List<String>>,
    color: Color,
    baseStyle: TextStyle
) {
    val cols = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.05f))
    ) {
        if (header.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().background(PurpleSoft.copy(alpha = if (color == Color.White) 0.7f else 0.18f))) {
                for (i in 0 until cols) {
                    Text(
                        buildInline(header.getOrElse(i) { "" }, color),
                        style = baseStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = color,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
        rows.forEachIndexed { idx, row ->
            if (idx > 0) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(color.copy(alpha = 0.08f)))
            }
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until cols) {
                    Text(
                        buildInline(row.getOrElse(i) { "" }, color),
                        style = baseStyle.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = color,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 解析

private fun parseMarkdown(src: String): List<MdBlock> {
    val lines = src.replace("\r\n", "\n").split('\n')
    val out = ArrayList<MdBlock>()
    var i = 0

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val t = line.trim()

        // 代码块
        if (t.startsWith("```")) {
            val buf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                buf.append(lines[i]).append('\n')
                i++
            }
            i++ // 跳过结束标记
            out.add(MdBlock.Code(buf.toString().trimEnd()))
            continue
        }

        if (t.isEmpty()) {
            if (out.isNotEmpty() && out.last() !is MdBlock.Gap) out.add(MdBlock.Gap)
            i++
            continue
        }

        // 分割线
        if (t == "---" || t == "***" || t == "___") {
            out.add(MdBlock.Divider)
            i++
            continue
        }

        // 标题
        if (t.startsWith("#")) {
            val level = t.takeWhile { it == '#' }.length.coerceAtMost(3)
            out.add(MdBlock.Heading(level, t.drop(level).trim()))
            i++
            continue
        }

        // 表格
        if (t.startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            val header = splitRow(t)
            i += 2
            val rows = ArrayList<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                rows.add(splitRow(lines[i]))
                i++
            }
            out.add(MdBlock.Table(header, rows))
            continue
        }

        // 引用
        if (t.startsWith(">")) {
            out.add(MdBlock.Quote(t.removePrefix(">").trim()))
            i++
            continue
        }

        // 无序列表
        val bulletMatch = Regex("^[-*+]\\s+(.*)$").find(t)
        if (bulletMatch != null) {
            out.add(MdBlock.Bullet(bulletMatch.groupValues[1]))
            i++
            continue
        }

        // 有序列表
        val numMatch = Regex("^(\\d+)[.)]\\s+(.*)$").find(t)
        if (numMatch != null) {
            out.add(MdBlock.Numbered(numMatch.groupValues[1], numMatch.groupValues[2]))
            i++
            continue
        }

        // 普通段落：合并连续行
        val sb = StringBuilder(t)
        i++
        while (i < lines.size) {
            val n = lines[i].trim()
            if (n.isEmpty() || n.startsWith("#") || n.startsWith("```") ||
                n.startsWith(">") || n.startsWith("|") ||
                Regex("^[-*+]\\s+").containsMatchIn(n) ||
                Regex("^\\d+[.)]\\s+").containsMatchIn(n)
            ) break
            sb.append('\n').append(n)
            i++
        }
        out.add(MdBlock.Para(sb.toString()))
    }
    return out
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.startsWith("|")) return false
    return t.all { it == '|' || it == '-' || it == ':' || it.isWhitespace() } && t.contains('-')
}

private fun splitRow(line: String): List<String> {
    var s = line.trim()
    if (s.startsWith("|")) s = s.substring(1)
    if (s.endsWith("|")) s = s.dropLast(1)
    return s.split('|').map { it.trim() }
}

// ---------------------------------------------------------------- 行内样式

private fun buildInline(src: String, base: Color): AnnotatedString {
    val b = AnnotatedString.Builder()
    var i = 0
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > i + 2) {
                    b.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(src.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    b.append(src[i]); i++
                }
            }

            src[i] == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    b.withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = if (base == Color.White) Color(0xFF6C5CE7) else Color.White,
                            background = if (base == Color.White) Color(0x11000000) else Color(0x22000000)
                        )
                    ) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    b.append(src[i]); i++
                }
            }

            src[i] == '*' && i + 1 < src.length && !src[i + 1].isWhitespace() -> {
                val end = src.indexOf('*', i + 1)
                if (end > i + 1) {
                    b.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(src.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    b.append(src[i]); i++
                }
            }

            src[i] == '[' -> {
                val close = src.indexOf("](", i)
                val end = if (close > 0) src.indexOf(')', close) else -1
                if (close > i && end > close) {
                    val label = src.substring(i + 1, close)
                    b.withStyle(
                        SpanStyle(
                            color = if (base == Color.White) Color(0xFF4D9EFF) else Color.White,
                            textDecoration = TextDecoration.Underline
                        )
                    ) { append(label) }
                    i = end + 1
                } else {
                    b.append(src[i]); i++
                }
            }

            else -> {
                b.append(src[i]); i++
            }
        }
    }
    return b.toAnnotatedString()
}
