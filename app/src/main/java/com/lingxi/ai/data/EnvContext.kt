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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 粗略定位（城市级即可），只用系统 LocationManager，不依赖 GMS。 */
object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun coarse(context: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // 先用系统缓存，通常立刻拿到
        runCatching {
            lm.getProviders(true).forEach { p ->
                val loc = lm.getLastKnownLocation(p)
                if (loc != null) return loc.latitude to loc.longitude
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
}

/**
 * Open-Meteo 当前天气。免费、不需要 key。
 * 用 current 接口（已在设备上验证可达），不再请求 hourly。
 */
object WeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val CODES = mapOf(
        0 to "晴", 1 to "大致晴朗", 2 to "多云", 3 to "阴",
        45 to "有雾", 48 to "雾凇",
        51 to "小毛毛雨", 53 to "毛毛雨", 55 to "大毛毛雨",
        61 to "小雨", 63 to "中雨", 65 to "大雨",
        66 to "冻雨", 67 to "强冻雨",
        71 to "小雪", 73 to "中雪", 75 to "大雪", 77 to "雪粒",
        80 to "阵雨", 81 to "强阵雨", 82 to "暴雨",
        85 to "阵雪", 86 to "强阵雪",
        95 to "雷阵雨", 96 to "雷阵雨伴冰雹", 99 to "强雷雨伴冰雹"
    )

    suspend fun summary(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + String.format(Locale.US, "%.3f", lat) +
            "&longitude=" + String.format(Locale.US, "%.3f", lon) +
            "&current=temperature_2m,weather_code&timezone=auto"

        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val current = JSONObject(body).optJSONObject("current") ?: return@use null

                val temp = current.optDouble("temperature_2m", Double.NaN)
                val code = current.optInt("weather_code", -1)
                val desc = CODES[code].orEmpty()

                val text = buildString {
                    if (desc.isNotBlank()) append(desc).append('，')
                    if (!temp.isNaN()) append("气温 ").append(temp.toInt()).append("℃")
                }.trim().trimEnd('，')

                text.ifBlank { null }
            }
        }.getOrNull()
    }
}