package com.lingxi.ai.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Course(
    val weekday: Int,
    val start: String,
    val end: String,
    val name: String,
    val place: String = "",
    val teacher: String = "",
    val weeks: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("weekday", weekday)
        put("start", start)
        put("end", end)
        put("name", name)
        put("place", place)
        put("teacher", teacher)
        put("weeks", weeks)
    }

    companion object {
        fun fromJson(o: JSONObject) = Course(
            weekday = o.optInt("weekday", 1).coerceIn(1, 7),
            start = o.optString("start"),
            end = o.optString("end"),
            name = o.optString("name"),
            place = o.optString("place"),
            teacher = o.optString("teacher"),
            weeks = o.optString("weeks")
        )
    }
}

object Timetable {

    private val WEEK = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun encode(list: List<Course>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun decode(raw: String): List<Course> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<Course>()
            for (i in 0 until arr.length()) out.add(Course.fromJson(arr.getJSONObject(i)))
            out.sortedWith(compareBy({ it.weekday }, { it.start }))
        }.getOrDefault(emptyList())
    }

    /** 从模型返回里抠出 JSON 数组，尽量容错。 */
    fun parse(raw: String): List<Course> {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        require(start >= 0 && end > start) { "模型没有返回 JSON 数组" }
        val body = raw.substring(start, end + 1)
        val arr = JSONArray(body)
        val out = ArrayList<Course>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("name").isBlank()) continue
            out.add(Course.fromJson(o))
        }
        require(out.isNotEmpty()) { "没有解析出课程" }
        return out.sortedWith(compareBy({ it.weekday }, { it.start }))
    }

    /**
     * 生成一段现实信息，供模型自然使用。
     */
    fun describe(courses: List<Course>, cal: Calendar = Calendar.getInstance()): String {
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val weekday = if (dow == Calendar.SUNDAY) 7 else dow - 1
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val date = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"

        val sb = StringBuilder()
        sb.append("现在是 $date ${WEEK[weekday]} ${hm(nowMin)}。")

        if (courses.isEmpty()) return sb.toString()

        val today = courses.filter { it.weekday == weekday }.sortedBy { it.start }

        if (today.isEmpty()) {
            sb.append("今天没有课。")
        } else {
            val current = today.firstOrNull { nowMin in toMin(it.start)..toMin(it.end) }
            val next = today.firstOrNull { toMin(it.start) > nowMin }

            if (current != null) {
                sb.append("正在上《${current.name}》")
                if (current.place.isNotBlank()) sb.append("（${current.place}）")
                sb.append("，${toMin(current.end) - nowMin} 分钟后下课。")
            }
            if (next != null) {
                sb.append("接下来 ${next.start} 上《${next.name}》")
                if (next.place.isNotBlank()) sb.append("，地点 ${next.place}")
                sb.append("。")
            }
            if (current == null && next == null) {
                sb.append("今天的课已经上完了。")
            }
        }

        // 整周课表：用户问「明天」「周四」时模型要答得出来
        sb.append("\n完整课表：\n")
        for (d in 1..7) {
            val list = courses.filter { it.weekday == d }.sortedBy { it.start }
            if (list.isEmpty()) continue
            sb.append(WEEK[d]).append("：")
            list.forEachIndexed { i, c ->
                if (i > 0) sb.append("；")
                sb.append("${c.start}-${c.end} ${c.name}")
                if (c.place.isNotBlank()) sb.append(" @${c.place}")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun toMin(hhmm: String): Int {
        val parts = hhmm.trim().split(':')
        val h = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun hm(min: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60)

    fun weekdayName(weekday: Int): String =
        WEEK.getOrElse(weekday) { "" }
}