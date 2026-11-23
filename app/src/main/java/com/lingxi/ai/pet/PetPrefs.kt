package com.lingxi.ai.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/** 桌宠开关、位置、留言队列。全部在本机。 */
object PetPrefs {

    private const val FILE = "vee_pet"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)

    fun setEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("enabled", value).apply()
    }

    fun messagesEnabled(c: Context): Boolean = sp(c).getBoolean("messages", true)

    fun setMessagesEnabled(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("messages", value).apply()
    }

    fun markPending(c: Context) = sp(c).edit().putBoolean("pending", true).apply()

    fun takePending(c: Context): Boolean {
        if (!sp(c).getBoolean("pending", false)) return false
        sp(c).edit().putBoolean("pending", false).apply()
        return true
    }

    fun position(c: Context): Pair<Int, Int>? {
        val x = sp(c).getInt("x", Int.MIN_VALUE)
        val y = sp(c).getInt("y", Int.MIN_VALUE)
        return if (x == Int.MIN_VALUE || y == Int.MIN_VALUE) null else x to y
    }

    fun savePosition(c: Context, x: Int, y: Int) {
        sp(c).edit().putInt("x", x).putInt("y", y).apply()
    }
}

/** 桌宠在桌面说的话，等用户打开 App 后并入当前对话。 */
object PetOutbox {

    private const val FILE = "vee_pet"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun push(c: Context, text: String) {
        if (text.isBlank()) return
        val arr = runCatching { JSONArray(sp(c).getString("outbox", "[]")) }.getOrElse { JSONArray() }
        arr.put(JSONObject().put("text", text).put("time", System.currentTimeMillis()))
        while (arr.length() > 20) arr.remove(0)
        sp(c).edit().putString("outbox", arr.toString()).apply()
    }

    fun drain(c: Context): List<String> {
        val arr = runCatching { JSONArray(sp(c).getString("outbox", "[]")) }.getOrElse { JSONArray() }
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i)?.optString("text").orEmpty()
            if (t.isNotBlank()) out.add(t)
        }
        if (out.isNotEmpty()) sp(c).edit().putString("outbox", "[]").apply()
        return out
    }
}

/** App 在前台时，桌宠说了话立刻并入当前对话。 */
object PetBus {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun register(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun unregister(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun emit() {
        listeners.forEach { runCatching { it() } }
    }
}
