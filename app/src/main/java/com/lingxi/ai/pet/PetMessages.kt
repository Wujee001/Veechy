package com.lingxi.ai.pet

import android.content.Context
import com.lingxi.ai.data.SettingsStore
import com.lingxi.ai.data.Timetable
import java.util.Calendar

/**
 * 桌宠的一句话。全部在本地生成：不耗 token、断网也能用。
 * 只引用确定的信息（时间、课表），不编造现实场景。
 */
object PetMessages {

    private fun store(c: Context) = SettingsStore(c)

    private fun lastOf(c: Context): String =
        c.getSharedPreferences("vee_pet", Context.MODE_PRIVATE).getString("last_msg", "") ?: ""

    private fun mark(c: Context, text: String) {
        c.getSharedPreferences("vee_pet", Context.MODE_PRIVATE)
            .edit().putString("last_msg", text).apply()
    }

    private fun pick(c: Context, list: List<String>): String {
        if (list.isEmpty()) return ""
        val last = lastOf(c)
        val fresh = list.filter { it != last }
        val text = (if (fresh.isNotEmpty()) fresh else list).random()
        mark(c, text)
        return text
    }

    fun scheduled(c: Context, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val nowMin = hour * 60 + cal.get(Calendar.MINUTE)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekday = if (dow == Calendar.SUNDAY) 7 else dow - 1

        val settings = store(c)
        val courses = if (settings.timetableEnabled) Timetable.decode(settings.timetableJson) else emptyList()
        val today = courses.filter { it.weekday == weekday }.sortedBy { it.start }

        // 正在上课
        val current = today.firstOrNull { nowMin in Timetable.toMin(it.start)..Timetable.toMin(it.end) }
        if (current != null) {
            val left = Timetable.toMin(current.end) - nowMin
            if (left > 0) return "《${current.name}》还有 $left 分钟下课，坐稳。"
        }

        // 一小时内要上课
        val next = today.firstOrNull {
            val s = Timetable.toMin(it.start)
            s > nowMin && s - nowMin <= 60
        }
        if (next != null) {
            return "${next.start} 有《${next.name}》，别踩点。"
        }

        return pick(
            c,
            when {
                hour in 5..9 -> listOf(
                    "早饭吃了吗？别空着肚子出门。",
                    "醒了就回我一声。",
                    "今天也别急，一件一件来。"
                )

                hour in 11..13 -> listOf(
                    "午饭别糊弄，好好吃。",
                    "吃完歇一会儿，再继续。",
                    "到饭点了，先去吃饭。"
                )

                hour in 17..19 -> listOf(
                    "晚饭吃了吗？",
                    "忙完这阵就歇一下。",
                    "别又拿零食当饭。"
                )

                hour >= 23 || hour <= 4 -> listOf(
                    "这个点还不睡，明天想顶着黑眼圈？",
                    "放下手机，去睡。",
                    "再熬一分钟我就要说你了。"
                )

                else -> listOf(
                    "忙你的，我在这儿。",
                    "有空了跟我说句话。",
                    "今天辛苦了，别硬撑。",
                    "刚想到你，就一句。",
                    "别光顾着学，抬头歇一会儿。",
                    "想听你说说今天的事。",
                    "我不催你，但记得回我。"
                )
            }
        )
    }

    fun tap(c: Context): String = pick(
        c,
        if (store(c).gender.contains("女")) listOf(
            "嗯？",
            "在。",
            "戳我做什么，说吧。",
            "有事就讲。",
            "再戳，我可要记账了。",
            "看够了吗？"
        ) else listOf(
            "嗯？",
            "在。",
            "戳我做什么，说吧。",
            "有事就讲。",
            "再戳，我可要记账了。",
            "看够了吗？"
        )
    )

    fun longPress(c: Context): String =
        if (store(c).gender.contains("女")) "想关掉我？去设置里说一声。" else "想关掉我？去设置里说一声。"
}