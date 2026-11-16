package com.lingxi.ai.data

/**
 * 清洗模型输出：
 * 1. 去掉 <think>…</think> 之类的内联思考块（MiniMax / 部分中转会塞进 content）
 * 2. 把开头的〔〕独白拆出来，作为心理活动
 */
object InnerVoice {

    private const val OPEN = '〔'
    private const val CLOSE = '〕'
    private const val MAX_THOUGHT = 180

    private val THINK_BLOCK = Regex("<(think|thinking|reasoning)>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
    private val THINK_TAIL = Regex("^\\s*<(think|thinking|reasoning)>[\\s\\S]*$", RegexOption.IGNORE_CASE)
    private val THINK_TAG = Regex("</?(think|thinking|reasoning)>", RegexOption.IGNORE_CASE)

    /** 清掉思考标签，流式未闭合时把整段视为思考过程隐藏。 */
    fun clean(raw: String): String {
        var s = THINK_BLOCK.replace(raw, "")
        s = THINK_TAIL.replace(s, "")
        s = THINK_TAG.replace(s, "")
        return s.trimStart()
    }

    fun split(raw: String): Pair<String?, String> {
        val t = clean(raw).trimStart()
        if (t.isEmpty() || t[0] != OPEN) return null to t

        val close = t.indexOf(CLOSE, 1)
        if (close > 0) {
            val thought = t.substring(1, close).trim()
            val body = t.substring(close + 1).trimStart()
            return thought.ifBlank { null } to body
        }

        // 还没闭合：短则当作正在写的独白，长则认为模型没按格式走
        val partial = t.substring(1).trim()
        return if (partial.length <= MAX_THOUGHT) partial.ifBlank { null } to "" else null to t
    }

    fun bodyOnly(raw: String): String = split(raw).second
}