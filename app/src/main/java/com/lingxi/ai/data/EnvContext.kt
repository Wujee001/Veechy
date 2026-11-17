package com.lingxi.ai.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 粗略定位（城市级即可），只用系统 LocationManager，不依赖 GMS。 */
object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun coarse(context: Context): Pair<Double, Double>? {
        if (!has(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // 先用系统缓存，通常立刻拿到
        runCatching {
            lm.getProviders(true).forEach { p ->
                lm.getLastKnownLocation(p)?.let { return it.latitude to it.longitude }
            }
        }

        return runCatching {
            suspendCancellableCoroutine<Pair<Double, Double>?> { cont ->
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val handler = Handler(Looper.getMainLooper())
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location.latitude to location.longitude)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

                runCatching { lm.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
                handler.postDelayed({
                    runCatching { lm.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(null)
                }, 6000)
            }
        }.getOrNull()
    }

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Open-Meteo：免费、不需要 key。 */
object WeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun summary(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + String.format(Locale.US, "%.3f", lat) +
            "&longitude=" + String.format(Locale.US, "%.3f", lon) +
            "&hourly=temperature_2m,precipitation_probability" +
            "&forecast_days=2&timezone=auto"

        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                val hourly = JSONObject(text).optJSONObject("hourly") ?: return null
                val times = hourly.optJSONArray("time") ?: return null
                val temps = hourly.optJSONArray("temperature_2m") ?: return null
                val precs = hourly.optJSONArray("precipitation_probability")

                val nowHour = SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.US).format(Date())
                var idx = -1
                for (i in 0 until times.length()) {
                    if (times.optString(i) == nowHour) {
                        idx = i
                        break
                    }
                }
                if (idx < 0) idx = 0

                val cur = temps.optDouble(idx, Double.NaN)
                var maxPrecip = 0
                var minT = Double.MAX_VALUE
                var maxT = -Double.MAX_VALUE
                val end = minOf(idx + 8, times.length())
                for (i in idx until end) {
                    val p = if (precs == null) 0 else precs.optInt(i, 0)
                    if (p > maxPrecip) maxPrecip = p
                    val t = temps.optDouble(i, Double.NaN)
                    if (!t.isNaN()) {
                        if (t < minT) minT = t
                        if (t > maxT) maxT = t
                    }
                }

                buildString {
                    if (!cur.isNaN()) append("气温 ").append(cur.toInt()).append("℃；")
                    if (maxPrecip >= 40) {
                        append("未来 8 小时可能下雨（降水概率最高 ").append(maxPrecip).append("%）；")
                    } else {
                        append("未来 8 小时无雨；")
                    }
                    if (minT != Double.MAX_VALUE) {
                        append("这 8 小时 ").append(minT.toInt()).append('~').append(maxT.toInt()).append("℃。")
                    }
                }.trim().ifBlank { null }
            }
        }.getOrNull()
    }
}

/** 系统日历：读未来 48 小时的日程。 */
object CalendarHelper {

    fun upcoming(context: Context, limit: Int = 6): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return ""

        val start = System.currentTimeMillis()
        val end = start + 48 * 3600_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )

        val items = ArrayList<String>()
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                val iTitle = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val iBegin = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val iLoc = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val iAllDay = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                while (c.moveToNext() && items.size < limit) {
                    val title = c.getString(iTitle)?.trim().orEmpty()
                    if (title.isBlank()) continue
                    val begin = c.getLong(iBegin)
                    val allDay = iAllDay >= 0 && c.getInt(iAllDay) == 1
                    val loc = if (iLoc >= 0) c.getString(iLoc)?.trim().orEmpty() else ""
                    val whenText = if (allDay) {
                        SimpleDateFormat("MM-dd 全天", Locale.getDefault()).format(Date(begin))
                    } else {
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(begin))
                    }
                    items.add(whenText + " " + title + if (loc.isNotBlank()) "@$loc" else "")
                }
            }
        }

        return items.joinToString("；")
    }

    fun todayLabel(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日"
    }
}