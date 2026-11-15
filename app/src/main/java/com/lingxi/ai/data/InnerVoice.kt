package com.lingxi.ai.data

/**
 * 把模型输出拆成「心理活动」与「正文」。
 * 约定：正文开头可有一句被〔〕包住的独白。
 * 流式未闭合时按正在书写的独白处理，过长则判定为正文误判并回退。
 */
object InnerVoice {

    private const val OPEN = '〔'
    private const val CLOSE = '〕'
    private const val MAX_THOUGHT = 180

    fun split(raw: String): Pair<String?, String> {
        val t = raw.trimStart()
        if (t.isEmpty() || t[0] != OPEN) return null to raw

        val close = t.indexOf(CLOSE, 1)
        if (close > 0) {
            val thought = t.substring(1, close).trim()
            val body = t.substring(close + 1).trimStart()
            return thought.ifBlank { null } to body
        }

        // 还没闭合：短则当作正在写的独白，长则认为模型没按格式走
        val partial = t.substring(1).trim()
        return if (partial.length <= MAX_THOUGHT) partial.ifBlank { null } to "" else null to raw
    }

    fun bodyOnly(raw: String): String = split(raw).second
}
