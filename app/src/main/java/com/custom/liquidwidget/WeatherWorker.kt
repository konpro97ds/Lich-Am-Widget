package com.custom.liquidwidget

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

class WeatherWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val locationMode = prefs.getString("location_mode", "AUTO") ?: "AUTO"

        val lat: Double
        val lon: Double

        if (locationMode == "MANUAL") {
            lat = prefs.getFloat("manual_lat", 10.8231f).toDouble()
            lon = prefs.getFloat("manual_lon", 106.6297f).toDouble()
            val manualCity = prefs.getString("manual_city_name", "TP. Hồ Chí Minh") ?: "TP. Hồ Chí Minh"
            prefs.edit().putString("last_city", "🏙️ $manualCity").apply()
        } else {
            val loc = getBestLastKnownLocation()
            lat = loc?.latitude ?: prefs.getFloat("last_lat", 10.8231f).toDouble()
            lon = loc?.longitude ?: prefs.getFloat("last_lon", 106.6297f).toDouble()
            if (loc != null) {
                prefs.edit()
                    .putFloat("last_lat", lat.toFloat())
                    .putFloat("last_lon", lon.toFloat())
                    .apply()
                resolveCityName(lat, lon)?.let { city ->
                    prefs.edit().putString("last_city", "📍 $city").apply()
                }
            }
        }

        return try {
            val urlStr = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=7"

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val current = json.getJSONObject("current")
            val curTemp = current.getDouble("temperature_2m").roundToInt()
            val curCode = current.getInt("weather_code")

            val daily = json.getJSONObject("daily")
            val codes = daily.getJSONArray("weather_code")
            val maxArr = daily.getJSONArray("temperature_2m_max")
            val minArr = daily.getJSONArray("temperature_2m_min")

            val todayMax = maxArr.getDouble(0).roundToInt()
            val todayMin = minArr.getDouble(0).roundToInt()

            val editor = prefs.edit()
            editor.putString("last_temp", "${curTemp}°")
            editor.putString("last_desc", mapWeatherCodeToDesc(curCode))
            editor.putString("last_range", "↑${todayMax}° ↓${todayMin}°")

            for (i in 0 until minOf(7, codes.length())) {
                val icon = mapWeatherCodeToIcon(codes.getInt(i))
                val hi = maxArr.getDouble(i).roundToInt()
                val lo = minArr.getDouble(i).roundToInt()
                editor.putString("fc_$i", "$icon\n${hi}°\n${lo}°")
            }
            editor.apply()

            refreshAllWidgets()
            Result.success()
        } catch (_: Exception) {
            refreshAllWidgets()
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in lm.getProviders(true)) {
            val l = try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null } ?: continue
            if (best == null || l.accuracy < best.accuracy) {
                best = l
            }
        }
        return best
    }

    @Suppress("DEPRECATION")
    private fun resolveCityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull() ?: return null
            addr.subAdminArea ?: addr.adminArea ?: addr.locality
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshAllWidgets() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LiquidWidgetProvider::class.java))
        for (id in ids) {
            LiquidWidgetProvider.updateAppWidget(context, manager, id)
        }
    }

    private fun mapWeatherCodeToDesc(code: Int): String = when (code) {
        0 -> "Trời quang"
        1, 2 -> "Ít mây"
        3 -> "Nhiều mây"
        45, 48 -> "Sương mù"
        51, 53, 55, 56, 57 -> "Mưa phùn"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Có mưa"
        71, 73, 75, 77, 85, 86 -> "Tuyết rơi"
        95, 96, 99 -> "Giông bão"
        else -> "Có mây"
    }

    private fun mapWeatherCodeToIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "⛅"
    }
}
