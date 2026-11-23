package com.lingxi.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量 LaTeX 渲染。
 *
 * 模型返回的数学通常只有一小撮结构：上下标、分数、根号、矩阵，以及 \in、\cap 之类的符号。
 * 这里不引入 MathJax / KaTeX：
 *  - 简单表达式直接转成 Unicode（α、∈、⟨、x² 等），可以自然嵌在正文里；
 *  - 带分数、根号、矩阵的表达式按块渲染，必要时横向滚动。
 * 解析失败时退化为原文本，不会崩。
 */

data class MathSeg(
    val text: String,
    val math: Boolean,
    val latex: String = "",
    val display: Boolean = false
)

private val COMPLEX_TOKENS = listOf(
    "\\frac", "\\dfrac", "\\tfrac", "\\sqrt", "\\begin{", "\\\\",
    "\\sum", "\\prod", "\\int", "\\lim", "\\overset", "\\underset"
)

fun latexIsComplex(src: String): Boolean = COMPLEX_TOKENS.any { src.contains(it) }

// ---------------------------------------------------------------- 分段

/** 把一段正文按 $…$、\(…\)、$$…$$、\[…\] 切成“文本 / 公式”。 */
fun splitMath(src: String): List<MathSeg> {
    val out = ArrayList<MathSeg>()
    val sb = StringBuilder()
    var i = 0

    fun flushText() {
        if (sb.isNotEmpty()) {
            out.add(MathSeg(sb.toString(), math = false))
            sb.setLength(0)
        }
    }

    while (i < src.length) {
        when {
            src.startsWith("$$", i) -> {
                val end = src.indexOf("$$", i + 2)
                if (end > i + 2) {
                    flushText()
                    out.add(MathSeg("", math = true, latex = src.substring(i + 2, end), display = true))
                    i = end + 2
                } else {
                    sb.append("$$"); i += 2
                }
            }

            src.startsWith("\\[", i) -> {
                val end = src.indexOf("\\]", i + 2)
                if (end > i + 2) {
                    flushText()
                    out.add(MathSeg("", math = true, latex = src.substring(i + 2, end), display = true))
                    i = end + 2
                } else {
                    sb.append("\\["); i += 2
                }
            }

            src.startsWith("\\(", i) -> {
                val end = src.indexOf("\\)", i + 2)
                if (end > i + 2) {
                    flushText()
                    out.add(MathSeg("", math = true, latex = src.substring(i + 2, end)))
                    i = end + 2
                } else {
                    sb.append("\\("); i += 2
                }
            }

            src[i] == '$' -> {
                val end = src.indexOf('$', i + 1)
                val next = src.getOrNull(i + 1)
                val ok = end > i + 1 && end - i < 300 && next != null && !next.isWhitespace()
                if (ok) {
                    flushText()
                    out.add(MathSeg("", math = true, latex = src.substring(i + 1, end)))
                    i = end + 1
                } else {
                    sb.append('$'); i++
                }
            }

            else -> {
                sb.append(src[i]); i++
            }
        }
    }
    flushText()
    return out
}

// ---------------------------------------------------------------- Unicode 化简

private val SYMBOLS = mapOf(
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
    "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
    "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
    "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
    "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    "in" to "∈", "notin" to "∉", "ni" to "∋", "cap" to "∩", "cup" to "∪",
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "setminus" to "∖", "emptyset" to "∅", "varnothing" to "∅",
    "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
    "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝",
    "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
    "infty" to "∞", "partial" to "∂", "nabla" to "∇", "forall" to "∀", "exists" to "∃",
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
    "Leftarrow" to "⇐", "leftrightarrow" to "↔", "mapsto" to "↦",
    "langle" to "⟨", "rangle" to "⟩", "lvert" to "|", "rvert" to "|",
    "lVert" to "‖", "rVert" to "‖", "mid" to "|", "vert" to "|", "Vert" to "‖",
    "sum" to "∑", "prod" to "∏", "int" to "∫", "oint" to "∮",
    "angle" to "∠", "perp" to "⊥", "parallel" to "∥", "therefore" to "∴", "because" to "∵",
    "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
    "circ" to "∘", "star" to "⋆", "ast" to "∗", "bullet" to "•",
    "quad" to "  ", "qquad" to "    ", ";" to " ", ":" to " ", "," to "",
    "!" to "", " " to " ", "\\" to " ", "%" to "%", "&" to " ", "#" to "#", "$" to "$",
    "_" to "_", "{" to "{", "}" to "}"
)

private val SUP_MAP = mapOf(
    '0' to "⁰", '1' to "¹", '2' to "²", '3' to "³", '4' to "⁴",
    '5' to "⁵", '6' to "⁶", '7' to "⁷", '8' to "⁸", '9' to "⁹",
    '+' to "⁺", '-' to "⁻", '=' to "⁼", '(' to "⁽", ')' to "⁾",
    'n' to "ⁿ", 'i' to "ⁱ", '.' to "·",
    'a' to "ᵃ", 'b' to "ᵇ", 'c' to "ᶜ", 'd' to "ᵈ", 'e' to "ᵉ", 'f' to "ᶠ",
    'g' to "ᵍ", 'h' to "ʰ", 'j' to "ʲ", 'k' to "ᵏ", 'l' to "ˡ", 'm' to "ᵐ",
    'o' to "ᵒ", 'p' to "ᵖ", 'r' to "ʳ", 's' to "ˢ", 't' to "ᵗ", 'u' to "ᵘ",
    'v' to "ᵛ", 'w' to "ʷ", 'x' to "ˣ", 'y' to "ʸ", 'z' to "ᶻ"
)

private val SUB_MAP = mapOf(
    '0' to "₀", '1' to "₁", '2' to "₂", '3' to "₃", '4' to "₄",
    '5' to "₅", '6' to "₆", '7' to "₇", '8' to "₈", '9' to "₉",
    '+' to "₊", '-' to "₋", '=' to "₌", '(' to "₍", ')' to "₎",
    'a' to "ₐ", 'e' to "ₑ", 'h' to "ₕ", 'i' to "ᵢ", 'j' to "ⱼ", 'k' to "ₖ",
    'l' to "ₗ", 'm' to "ₘ", 'n' to "ₙ", 'o' to "ₒ", 'p' to "ₚ", 'r' to "ᵣ",
    's' to "ₛ", 't' to "ₜ", 'u' to "ᵤ", 'v' to "ᵥ", 'x' to "ₓ"
)

private fun toSup(t: String): String {
    val b = StringBuilder()
    for (c in t) b.append(SUP_MAP[c] ?: return "^($t)")
    return b.toString()
}

private fun toSub(t: String): String {
    val b = StringBuilder()
    for (c in t) b.append(SUB_MAP[c] ?: return "_($t)")
    return b.toString()
}

private fun readGroup(s: String, idx: Int): Pair<String, Int>? {
    var i = idx
    while (i < s.length && s[i].isWhitespace()) i++
    if (i >= s.length) return null
    if (s[i] == '{') {
        val start = i + 1
        var depth = 0
        while (i < s.length) {
            if (s[i] == '{') depth++
            else if (s[i] == '}') {
                depth--
                if (depth == 0) return s.substring(start, i) to (i + 1)
            }
            i++
        }
        return s.substring(start) to s.length
    }
    if (s[i] == '\\') {
        var j = i + 1
        while (j < s.length && s[j].isLetter()) j++
        return s.substring(i, j) to j
    }
    return s.substring(i, i + 1) to (i + 1)
}

/** 把 LaTeX 化简成可以放进普通 Text 的字符串。 */
fun latexToUnicode(src: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '\\' -> {
                if (i + 1 >= src.length) { i++; continue }
                val next = src[i + 1]
                if (!next.isLetter()) {
                    // 反斜杠 + 符号
                    val sym = SYMBOLS[next.toString()]
                    if (sym != null) sb.append(sym) else sb.append(next)
                    i += 2
                    continue
                }
                var j = i + 1
                while (j < src.length && src[j].isLetter()) j++
                val cmd = src.substring(i + 1, j)

                when (cmd) {
                    "frac", "dfrac", "tfrac" -> {
                        val a = readGroup(src, j)
                        val b = if (a != null) readGroup(src, a.second) else null
                        if (a != null && b != null) {
                            val num = latexToUnicode(a.first)
                            val den = latexToUnicode(b.first)
                            sb.append(if (num.length > 1) "($num)" else num)
                            sb.append('/')
                            sb.append(if (den.length > 1) "($den)" else den)
                            i = b.second
                        } else {
                            i = j
                        }
                    }

                    "sqrt" -> {
                        var k = j
                        if (k < src.length && src[k] == '[') {
                            val close = src.indexOf(']', k)
                            if (close > 0) k = close + 1
                        }
                        val g = readGroup(src, k)
                        if (g != null) {
                            sb.append('√').append(latexToUnicode(g.first))
                            i = g.second
                        } else {
                            i = j
                        }
                    }

                    "text", "textrm", "mathrm", "operatorname", "textbf", "mathbf", "mathit" -> {
                        val g = readGroup(src, j)
                        if (g != null) {
                            sb.append(g.first)
                            i = g.second
                        } else {
                            i = j
                        }
                    }

                    "overline" -> {
                        val g = readGroup(src, j)
                        if (g != null) {
                            val t = latexToUnicode(g.first)
                            t.forEach { sb.append(it).append('̅') }
                            i = g.second
                        } else {
                            i = j
                        }
                    }

                    "left", "right", "big", "Big", "bigg", "Bigg" -> {
                        i = j
                    }

                    "begin", "end" -> {
                        val g = readGroup(src, j)
                        i = if (g != null) g.second else j
                    }

                    else -> {
                        val sym = SYMBOLS[cmd]
                        if (sym != null) {
                            sb.append(sym)
                        } else {
                            // 不认识的命令：保留命令名，至少不丢信息
                            sb.append(cmd)
                        }
                        i = j
                    }
                }
            }

            c == '^' || c == '_' -> {
                val g = readGroup(src, i + 1)
                if (g != null) {
                    val t = latexToUnicode(g.first)
                    sb.append(if (c == '^') toSup(t) else toSub(t))
                    i = g.second
                } else {
                    i++
                }
            }

            c == '{' || c == '}' -> i++

            c == '&' -> { sb.append(' '); i++ }

            c == '~' -> { sb.append(' '); i++ }

            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString().replace(Regex(" {2,}"), " ").trim()
}

// ---------------------------------------------------------------- 块级结构

sealed interface MathNode {
    data class Txt(val text: String, val bold: Boolean = false) : MathNode
    data class Frac(val num: String, val den: String) : MathNode
    data class Sqrt(val inner: String) : MathNode
    data class Mat(val env: String, val rows: List<List<String>>) : MathNode
}

fun parseMathNodes(src: String): List<MathNode> {
    val out = ArrayList<MathNode>()
    val buf = StringBuilder()
    var i = 0

    fun flush() {
        if (buf.isNotBlank()) {
            out.add(MathNode.Txt(buf.toString()))
            buf.setLength(0)
        }
    }

    while (i < src.length) {
        if (src.startsWith("\\begin{", i)) {
            val close = src.indexOf('}', i + 7)
            if (close > 0) {
                val env = src.substring(i + 7, close)
                val endTok = "\\end{$env}"
                val end = src.indexOf(endTok, close + 1)
                if (end > 0) {
                    flush()
                    out.add(MathNode.Mat(env, parseMatrixBody(src.substring(close + 1, end))))
                    i = end + endTok.length
                    continue
                }
            }
        }

        val fracCmd = when {
            src.startsWith("\\frac", i) -> 5
            src.startsWith("\\dfrac", i) -> 6
            src.startsWith("\\tfrac", i) -> 6
            else -> 0
        }
        if (fracCmd > 0) {
            val a = readGroup(src, i + fracCmd)
            val b = if (a != null) readGroup(src, a.second) else null
            if (a != null && b != null) {
                flush()
                out.add(MathNode.Frac(a.first, b.first))
                i = b.second
                continue
            }
        }

        if (src.startsWith("\\sqrt", i)) {
            var k = i + 5
            if (k < src.length && src[k] == '[') {
                val c2 = src.indexOf(']', k)
                if (c2 > 0) k = c2 + 1
            }
            val g = readGroup(src, k)
            if (g != null) {
                flush()
                out.add(MathNode.Sqrt(g.first))
                i = g.second
                continue
            }
        }

        buf.append(src[i]); i++
    }
    flush()
    return out
}

private fun parseMatrixBody(body: String): List<List<String>> {
    return body.split("\\\\")
        .map { it.replace("\\hline", "").trim() }
        .filter { it.isNotEmpty() }
        .map { line -> line.split('&').map { it.trim() } }
}

@Composable
fun MathBlock(latex: String, color: Color, baseStyle: TextStyle) {
    val nodes = remember(latex) { runCatching { parseMathNodes(latex) }.getOrDefault(emptyList()) }
    if (nodes.isEmpty()) {
        Text(latex, style = baseStyle, color = color)
        return
    }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .horizontalScroll(scroll)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            nodes.forEach { node -> MathNodeView(node, color, baseStyle) }
        }
    }
}

@Composable
private fun MathNodeView(node: MathNode, color: Color, baseStyle: TextStyle) {
    when (node) {
        is MathNode.Txt -> Text(
            latexToUnicode(node.text),
            style = baseStyle.copy(
                fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = FontStyle.Italic
            ),
            color = color
        )

        is MathNode.Frac -> Column(
            Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(latexToUnicode(node.num), style = baseStyle.copy(fontSize = baseStyle.fontSize * 0.92f), color = color)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(color.copy(alpha = 0.75f))
            )
            Text(latexToUnicode(node.den), style = baseStyle.copy(fontSize = baseStyle.fontSize * 0.92f), color = color)
        }

        is MathNode.Sqrt -> Row(verticalAlignment = Alignment.Bottom) {
            Text("√", style = baseStyle.copy(fontSize = baseStyle.fontSize * 1.25f), color = color)
            Column(Modifier.width(IntrinsicSize.Max)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(color.copy(alpha = 0.75f))
                )
                Text(latexToUnicode(node.inner), style = baseStyle, color = color)
            }
        }

        is MathNode.Mat -> MatrixView(node.env, node.rows, color, baseStyle)
    }
}

@Composable
private fun MatrixView(env: String, rows: List<List<String>>, color: Color, baseStyle: TextStyle) {
    val cellStyle = baseStyle.copy(fontSize = baseStyle.fontSize * 0.95f)
    val plain = env in setOf("matrix", "smallmatrix", "aligned", "alignedat", "array", "split", "gathered")
    val pipe = env == "vmatrix" || env == "Vmatrix"
    val cases = env == "cases" || env == "rcases"
    Row(
        Modifier.height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            pipe -> MathBar(color)
            cases -> MathBrace(color)
            !plain -> MathBracket(left = true, color = color)
        }
        Column(
            Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cell ->
                        Text(latexToUnicode(cell), style = cellStyle, color = color)
                    }
                }
            }
        }
        when {
            pipe -> MathBar(color)
            !plain && !cases -> MathBracket(left = false, color = color)
        }
    }
}

@Composable
private fun MathBracket(left: Boolean, color: Color) {
    Canvas(Modifier.width(8.dp).fillMaxHeight()) {
        val w = size.width
        val h = size.height
        val s = 1.4.dp.toPx()
        val x = if (left) w - s / 2 else s / 2
        val tip = if (left) w * 0.15f else w * 0.85f
        drawLine(color, Offset(x, 0f), Offset(x, h), strokeWidth = s)
        drawLine(color, Offset(x, 0f), Offset(tip, 0f), strokeWidth = s)
        drawLine(color, Offset(x, h), Offset(tip, h), strokeWidth = s)
    }
}

@Composable
private fun MathBar(color: Color) {
    Canvas(Modifier.width(6.dp).fillMaxHeight()) {
        drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.4.dp.toPx())
    }
}

@Composable
private fun MathBrace(color: Color) {
    Canvas(Modifier.width(9.dp).fillMaxHeight()) {
        val w = size.width
        val h = size.height
        val s = 1.4.dp.toPx()
        drawLine(color, Offset(w * 0.7f, 0f), Offset(w * 0.3f, h * 0.18f), strokeWidth = s)
        drawLine(color, Offset(w * 0.3f, h * 0.18f), Offset(w * 0.3f, h * 0.45f), strokeWidth = s)
        drawLine(color, Offset(w * 0.3f, h * 0.45f), Offset(w * 0.05f, h * 0.5f), strokeWidth = s)
        drawLine(color, Offset(w * 0.05f, h * 0.5f), Offset(w * 0.3f, h * 0.55f), strokeWidth = s)
        drawLine(color, Offset(w * 0.3f, h * 0.55f), Offset(w * 0.3f, h * 0.82f), strokeWidth = s)
        drawLine(color, Offset(w * 0.3f, h * 0.82f), Offset(w * 0.7f, h), strokeWidth = s)
    }
}

// ---------------------------------------------------------------- 正文混排

private sealed interface BodyItem
private data class BodyText(val text: String) : BodyItem
private data class BodyMath(val latex: String) : BodyItem

private fun bodyItems(src: String): List<BodyItem> {
    val segs = splitMath(src)
    val out = ArrayList<BodyItem>()
    val sb = StringBuilder()

    fun flush() {
        if (sb.isNotEmpty()) {
            out.add(BodyText(sb.toString()))
            sb.setLength(0)
        }
    }

    segs.forEach { s ->
        if (s.math && (s.display || latexIsComplex(s.latex))) {
            flush()
            out.add(BodyMath(s.latex))
        } else if (s.math) {
            sb.append(latexToUnicode(s.latex))
        } else {
            sb.append(s.text)
        }
    }
    flush()
    return out
}

/** 正文：普通段落渲染成一个 Text（可正常换行）；含分数/矩阵时拆成文本 + 公式块。 */
@Composable
fun RichBody(
    text: String,
    color: Color,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val items = remember(text) { bodyItems(text) }
    if (items.none { it is BodyMath }) {
        Text(buildInline(text, color), style = baseStyle, color = color, modifier = modifier)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { item ->
                when (item) {
                    is BodyText -> Text(buildInline(item.text, color), style = baseStyle, color = color)
                    is BodyMath -> MathBlock(item.latex, color, baseStyle)
                }
            }
        }
    }
}