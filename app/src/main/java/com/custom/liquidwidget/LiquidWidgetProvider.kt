package com.custom.liquidwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextClock
import android.widget.TextView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.Calendar
import java.util.Random
import java.util.concurrent.TimeUnit

class LiquidWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, LiquidWidgetProvider::class.java))

        when (intent.action) {
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "com.custom.liquidwidget.PLAY_EFFECT",
            "com.custom.liquidwidget.TICK_CLOCK" -> {
                for (id in ids) updateAppWidget(context, manager, id)
                schedulePeriodicRefresh(context)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWeatherUpdate(context)
        schedulePeriodicRefresh(context)
    }

    companion object {
        data class RenderConfig(
            val clockSize: Float = 36f,
            val textScale: Float = 0.95f,
            val bgAlpha: Int = 48,
            val bgColorHex: String = "#0F172A",
            val textColorHex: String = "#FFFFFF",
            val accentColorHex: String = "#FFD54F",
            val layoutMode: Int = 0,
            val fontType: String = "DEFAULT",
            val enableEffects: Boolean = true,
            val testEffectMode: String = "AUTO",
            val overrideCity: String? = null
        )

        private fun buildClockPendingIntent(context: Context): PendingIntent {
            val clockPackages = listOf(
                "com.coloros.alarmclock",
                "com.oneplus.deskclock",
                "com.android.deskclock",
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage"
            )
            for (pkg in clockPackages) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return PendingIntent.getActivity(context, 11, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
            val alarmIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(context, 11, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun buildCalendarPendingIntent(context: Context): PendingIntent {
            val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            ContentUris.appendId(builder, System.currentTimeMillis())
            val calIntent = Intent(Intent.ACTION_VIEW).setData(builder.build()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(context, 12, calIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun buildWeatherPendingIntent(context: Context): PendingIntent {
            val weatherPackages = listOf(
                "com.coloros.weather2",
                "com.oplus.weather",
                "com.miui.weather2",
                "com.sec.android.daemonapp",
                "com.google.android.apps.weather"
            )
            for (pkg in weatherPackages) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return PendingIntent.getActivity(context, 13, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=thoi+tiet")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(context, 13, webIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Dựng khung Xem Trước trong MainActivity dùng chung 100% layout & logic với Widget thật
         */
        fun renderPreviewView(context: Context, root: View, cfg: RenderConfig) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val mainColor = Color.parseColor(cfg.textColorHex)
            val accentColor = Color.parseColor(cfg.accentColorHex)
            val typeface = loadCustomTypeface(context, cfg.fontType)

            val cal = Calendar.getInstance()
            val dd = cal.get(Calendar.DAY_OF_MONTH)
            val mm = cal.get(Calendar.MONTH) + 1
            val yy = cal.get(Calendar.YEAR)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isNight = (hour >= 18 || hour < 6)

            val lunarToday = LunarCalendar.getLunarInfo(dd, mm, yy)
            val holiday = LunarCalendar.getVietnameseHoliday(dd, mm, yy)
            val descStr = prefs.getString("last_desc", "Ít mây") ?: "Ít mây"

            val activeEffect = if (!cfg.enableEffects) "NONE"
            else if (cfg.testEffectMode != "AUTO") cfg.testEffectMode
            else if (holiday.themeType != "NONE") holiday.themeType
            else detectWeatherEffect(descStr, isNight)

            root.findViewById<ImageView>(R.id.iv_glass_bg)?.setImageBitmap(
                drawLiquidGlassBitmap(cfg.bgColorHex, cfg.bgAlpha, 56f, activeEffect, isNight, lunarToday.first)
            )
            root.findViewById<ProgressBar>(R.id.pb_glass_shimmer)?.visibility = if (cfg.enableEffects) View.VISIBLE else View.GONE

            val showRain = cfg.enableEffects && (activeEffect == "RAIN" || activeEffect == "THUNDER")
            val showSun = cfg.enableEffects && (activeEffect == "SUN" || activeEffect == "SUN_CLOUD" || activeEffect == "MID_AUTUMN")
            val showMoon = cfg.enableEffects && (activeEffect == "MOON" || activeEffect == "MOON_CLOUD")
            val showCloud = cfg.enableEffects && (activeEffect == "CLOUD" || activeEffect == "SUN" || activeEffect == "SUN_CLOUD" || activeEffect == "MOON_CLOUD" || activeEffect == "RAIN" || activeEffect == "THUNDER")
            val showThunder = cfg.enableEffects && (activeEffect == "THUNDER")
            val showTet = cfg.enableEffects && (activeEffect == "TET" || activeEffect == "NATIONAL")
            val showXmas = cfg.enableEffects && (activeEffect == "XMAS")

            root.findViewById<View>(R.id.pb_weather_rain)?.visibility = if (showRain) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_sun)?.visibility = if (showSun) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_moon)?.visibility = if (showMoon) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_cloud)?.visibility = if (showCloud) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_thunder)?.visibility = if (showThunder) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_tet)?.visibility = if (showTet) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.pb_weather_xmas)?.visibility = if (showXmas) View.VISIBLE else View.GONE

            intArrayOf(R.id.tc_hour, R.id.tc_hour_c, R.id.tc_hour_r, R.id.tc_minute, R.id.tc_minute_c, R.id.tc_minute_r).forEach { id ->
                root.findViewById<TextClock>(id)?.apply {
                    setTextColor(mainColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.clockSize)
                    this.typeface = typeface
                }
            }

            root.findViewById<View>(R.id.layout_header_left)?.visibility = if (cfg.layoutMode == 0) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.layout_header_center)?.visibility = if (cfg.layoutMode == 1) View.VISIBLE else View.GONE
            root.findViewById<View>(R.id.layout_header_right)?.visibility = if (cfg.layoutMode == 2) View.VISIBLE else View.GONE

            val greeting = when (hour) {
                in 5..10 -> "Chào buổi sáng ⚙"
                in 11..13 -> "Chào buổi trưa ⚙"
                in 14..17 -> "Chào buổi chiều ⚙"
                else -> "Chào buổi tối ⚙"
            }
            val dayOfWeek = arrayOf("", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")[cal.get(Calendar.DAY_OF_WEEK)]
            val solarStr = "$dayOfWeek, $dd Thg $mm, $yy"
            val baseLunarStr = lunarToday.third
            val upcomingStr = LunarCalendar.getNextUpcomingHoliday(dd, mm, yy)
            val displayLunarOrHoliday = when {
                holiday.name.isNotEmpty() -> "${holiday.name} · $baseLunarStr"
                upcomingStr.isNotEmpty() -> "$baseLunarStr · $upcomingStr"
                else -> baseLunarStr
            }
            val tempStr = prefs.getString("last_temp", "28°") ?: "28°"
            val cityStr = cfg.overrideCity ?: (prefs.getString("last_city", "🏙️ TP. Hồ Chí Minh") ?: "🏙️ TP. Hồ Chí Minh")
            val rangeStr = prefs.getString("last_range", "↑32° ↓24°") ?: "↑32° ↓24°"

            root.findViewById<TextView>(R.id.tv_greeting)?.apply {
                text = greeting
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * cfg.textScale)
                setTextColor(mainColor)
                this.typeface = typeface
            }

            root.findViewById<ImageView>(R.id.iv_solar_custom)?.setImageBitmap(drawTextBitmap(context, solarStr, 12.5f * cfg.textScale, mainColor, typeface))
            root.findViewById<ImageView>(R.id.iv_lunar_custom)?.setImageBitmap(drawTextBitmap(context, displayLunarOrHoliday, 12f * cfg.textScale, accentColor, typeface))
            root.findViewById<ImageView>(R.id.iv_temp_custom)?.setImageBitmap(drawTextBitmap(context, tempStr, 31f * cfg.textScale, mainColor, typeface))

            fun bindTv(id: Int, txt: String, sp: Float, col: Int) {
                root.findViewById<TextView>(id)?.apply {
                    text = txt
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * cfg.textScale)
                    setTextColor(col)
                    this.typeface = typeface
                }
            }

            bindTv(R.id.tv_weather_desc, descStr, 12f, mainColor)
            bindTv(R.id.tv_city, cityStr, 11f, mainColor)
            bindTv(R.id.tv_temp_range, rangeStr, 11f, mainColor)
            bindTv(R.id.tv_sub_center, "$solarStr · $displayLunarOrHoliday · $tempStr $descStr", 11.5f, mainColor)
            bindTv(R.id.tv_weather_left_r, "$tempStr · $descStr · $cityStr", 12f, mainColor)
            bindTv(R.id.tv_solar_left_r, solarStr, 11.5f, mainColor)
            bindTv(R.id.tv_lunar_left_r, displayLunarOrHoliday, 11f, accentColor)
            bindTv(R.id.tv_range_right_r, rangeStr, 11f, mainColor)

            // Lịch tuần 7 ngày
            val weekCal = Calendar.getInstance()
            val currentDow = weekCal.get(Calendar.DAY_OF_WEEK)
            val offsetToMon = if (currentDow == Calendar.SUNDAY) -6 else Calendar.MONDAY - currentDow
            val todayIndexInWeek = if (currentDow == Calendar.SUNDAY) 6 else currentDow - Calendar.MONDAY
            weekCal.add(Calendar.DAY_OF_MONTH, offsetToMon)

            val dayIds = intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
            val labels = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

            for (i in 0..6) {
                val d = weekCal.get(Calendar.DAY_OF_MONTH)
                val m = weekCal.get(Calendar.MONTH) + 1
                val y = weekCal.get(Calendar.YEAR)
                val lunar = LunarCalendar.getLunarInfo(d, m, y)

                val nextCal = (weekCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val nextLunar = LunarCalendar.getLunarInfo(nextCal.get(Calendar.DAY_OF_MONTH), nextCal.get(Calendar.MONTH) + 1, nextCal.get(Calendar.YEAR))
                val isGoldDotDay = (lunar.first == 14 || lunar.first == 15 || lunar.first == 1 || nextLunar.first == 1)
                val dayHoliday = LunarCalendar.getVietnameseHoliday(d, m, y)
                val isMajorHoliday = dayHoliday.themeType == "TET" || dayHoliday.themeType == "NATIONAL"

                val baseText = "${labels[i]}\n$d\n${lunar.first}/${lunar.second}"
                root.findViewById<TextView>(dayIds[i])?.apply {
                    if (isGoldDotDay) {
                        val fullText = "$baseText●"
                        val spannable = SpannableString(fullText)
                        val dotStart = fullText.length - 1
                        spannable.setSpan(ForegroundColorSpan(Color.parseColor("#FDE047")), dotStart, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        spannable.setSpan(RelativeSizeSpan(1.35f), dotStart, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        text = spannable
                    } else {
                        text = baseText
                    }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * cfg.textScale)
                    this.typeface = typeface
                    if (i == todayIndexInWeek) {
                        setBackgroundResource(R.drawable.bg_glass_pill)
                        setTextColor(accentColor)
                    } else {
                        setBackgroundResource(0)
                        val isRedDay = (i == 6) || isMajorHoliday
                        setTextColor(if (isRedDay) Color.parseColor("#FF8A80") else mainColor)
                    }
                }
                weekCal.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Dự báo 7 ngày
            val fcIds = intArrayOf(R.id.fc_0, R.id.fc_1, R.id.fc_2, R.id.fc_3, R.id.fc_4, R.id.fc_5, R.id.fc_6)
            for (i in 0..6) {
                val colHeader = if (i == todayIndexInWeek) "Nay" else labels[i]
                val forecastOffset = i - todayIndexInWeek
                val content = if (forecastOffset >= 0) {
                    val raw = prefs.getString("fc_$forecastOffset", "⛅\n32°\n24°")
                    "$colHeader\n$raw"
                } else {
                    "$colHeader\n·\n--°\n--°"
                }
                root.findViewById<TextView>(fcIds[i])?.apply {
                    text = content
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * cfg.textScale)
                    setTextColor(if (i == todayIndexInWeek) accentColor else mainColor)
                    this.typeface = typeface
                }
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_liquid)
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

            val clockSize = prefs.getInt("clock_size", 36).toFloat()
            val textScale = prefs.getInt("text_scale", 95) / 100f
            val bgAlpha = prefs.getInt("bg_alpha", 48)
            val bgColorHex = prefs.getString("bg_color", "#0F172A") ?: "#0F172A"
            val textColorHex = prefs.getString("text_color", "#FFFFFF") ?: "#FFFFFF"
            val accentColorHex = prefs.getString("accent_color", "#FFD54F") ?: "#FFD54F"
            val layoutMode = prefs.getInt("layout_mode", 0)
            val fontType = prefs.getString("font_type", "DEFAULT") ?: "DEFAULT"
            val enableEffects = prefs.getBoolean("enable_effects", true)
            val testEffectMode = prefs.getString("test_effect", "AUTO") ?: "AUTO"

            val mainColor = Color.parseColor(textColorHex)
            val accentColor = Color.parseColor(accentColorHex)
            val typeface = loadCustomTypeface(context, fontType)

            val cal = Calendar.getInstance()
            val dd = cal.get(Calendar.DAY_OF_MONTH)
            val mm = cal.get(Calendar.MONTH) + 1
            val yy = cal.get(Calendar.YEAR)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isNight = (hour >= 18 || hour < 6)

            val lunarToday = LunarCalendar.getLunarInfo(dd, mm, yy)
            val lunarDay = lunarToday.first
            val holiday = LunarCalendar.getVietnameseHoliday(dd, mm, yy)
            val descStr = prefs.getString("last_desc", "Đang tải...") ?: "Đang tải..."

            val activeEffect = if (!enableEffects) "NONE"
            else if (testEffectMode != "AUTO") testEffectMode
            else if (holiday.themeType != "NONE") holiday.themeType
            else detectWeatherEffect(descStr, isNight)

            views.setImageViewBitmap(
                R.id.iv_glass_bg,
                drawLiquidGlassBitmap(bgColorHex, bgAlpha, 56f, activeEffect, isNight, lunarDay)
            )
            views.setViewVisibility(R.id.pb_glass_shimmer, if (enableEffects) View.VISIBLE else View.GONE)

            val showRain = enableEffects && (activeEffect == "RAIN" || activeEffect == "THUNDER")
            val showSun = enableEffects && (activeEffect == "SUN" || activeEffect == "SUN_CLOUD" || activeEffect == "MID_AUTUMN")
            val showMoon = enableEffects && (activeEffect == "MOON" || activeEffect == "MOON_CLOUD")
            val showCloud = enableEffects && (activeEffect == "CLOUD" || activeEffect == "SUN" || activeEffect == "SUN_CLOUD" || activeEffect == "MOON_CLOUD" || activeEffect == "RAIN" || activeEffect == "THUNDER")
            val showThunder = enableEffects && (activeEffect == "THUNDER")
            val showTet = enableEffects && (activeEffect == "TET" || activeEffect == "NATIONAL")
            val showXmas = enableEffects && (activeEffect == "XMAS")

            views.setViewVisibility(R.id.pb_weather_rain, if (showRain) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_sun, if (showSun) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_moon, if (showMoon) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_cloud, if (showCloud) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_thunder, if (showThunder) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_tet, if (showTet) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_xmas, if (showXmas) View.VISIBLE else View.GONE)

            val hourIds = intArrayOf(R.id.tc_hour, R.id.tc_hour_c, R.id.tc_hour_r)
            val minIds = intArrayOf(R.id.tc_minute, R.id.tc_minute_c, R.id.tc_minute_r)
            for (hId in hourIds) {
                views.setTextColor(hId, mainColor)
                views.setTextViewTextSize(hId, TypedValue.COMPLEX_UNIT_SP, clockSize)
                views.setCharSequence(hId, "setFormat24Hour", "HH")
                views.setCharSequence(hId, "setFormat12Hour", "HH")
            }
            for (mId in minIds) {
                views.setTextColor(mId, mainColor)
                views.setTextViewTextSize(mId, TypedValue.COMPLEX_UNIT_SP, clockSize)
                views.setCharSequence(mId, "setFormat24Hour", "mm")
                views.setCharSequence(mId, "setFormat12Hour", "mm")
            }

            views.setViewVisibility(R.id.pb_colon, View.VISIBLE)
            views.setViewVisibility(R.id.pb_colon_c, View.VISIBLE)
            views.setViewVisibility(R.id.pb_colon_r, View.VISIBLE)

            views.setViewVisibility(R.id.layout_header_left, if (layoutMode == 0) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.layout_header_center, if (layoutMode == 1) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.layout_header_right, if (layoutMode == 2) View.VISIBLE else View.GONE)

            val greeting = when (hour) {
                in 5..10 -> "Chào buổi sáng ⚙"
                in 11..13 -> "Chào buổi trưa ⚙"
                in 14..17 -> "Chào buổi chiều ⚙"
                else -> "Chào buổi tối ⚙"
            }
            val dayOfWeek = arrayOf("", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")[cal.get(Calendar.DAY_OF_WEEK)]
            val solarStr = "$dayOfWeek,$dd Thg $mm,$yy"
            val baseLunarStr = lunarToday.third
            val upcomingStr = LunarCalendar.getNextUpcomingHoliday(dd, mm, yy)
            val displayLunarOrHoliday = when {
                holiday.name.isNotEmpty() -> "${holiday.name} ·$baseLunarStr"
                upcomingStr.isNotEmpty() -> "$baseLunarStr · $upcomingStr"
                else -> baseLunarStr
            }
            val tempStr = prefs.getString("last_temp", "--°") ?: "--°"
            val cityStr = prefs.getString("last_city", "🏙️ TP. Hồ Chí Minh") ?: "🏙️ TP. Hồ Chí Minh"
            val rangeStr = prefs.getString("last_range", "↑--° ↓--°") ?: "↑--° ↓--°"

            views.setTextViewText(R.id.tv_greeting, greeting)
            views.setTextViewTextSize(R.id.tv_greeting, TypedValue.COMPLEX_UNIT_SP, 10.5f * textScale)
            views.setTextColor(R.id.tv_greeting, mainColor)

            views.setImageViewBitmap(R.id.iv_solar_custom, drawTextBitmap(context, solarStr, 12.5f * textScale, mainColor, typeface))
            views.setImageViewBitmap(R.id.iv_lunar_custom, drawTextBitmap(context, displayLunarOrHoliday, 12f * textScale, accentColor, typeface))
            views.setImageViewBitmap(R.id.iv_temp_custom, drawTextBitmap(context, tempStr, 31f * textScale, mainColor, typeface))
            views.setTextViewText(R.id.tv_weather_desc, descStr)
            views.setTextViewText(R.id.tv_city, cityStr)
            views.setTextViewText(R.id.tv_temp_range, rangeStr)
            views.setTextViewTextSize(R.id.tv_weather_desc, TypedValue.COMPLEX_UNIT_SP, 12f * textScale)
            views.setTextViewTextSize(R.id.tv_city, TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
            views.setTextViewTextSize(R.id.tv_temp_range, TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
            views.setTextColor(R.id.tv_weather_desc, mainColor)
            views.setTextColor(R.id.tv_city, mainColor)
            views.setTextColor(R.id.tv_temp_range, mainColor)

            views.setTextViewText(R.id.tv_sub_center, "$solarStr · $displayLunarOrHoliday · $tempStr$descStr")
            views.setTextViewTextSize(R.id.tv_sub_center, TypedValue.COMPLEX_UNIT_SP, 11.5f * textScale)
            views.setTextColor(R.id.tv_sub_center, mainColor)

            views.setTextViewText(R.id.tv_weather_left_r, "$tempStr · $descStr · $cityStr")
            views.setTextViewText(R.id.tv_solar_left_r, solarStr)
            views.setTextViewText(R.id.tv_lunar_left_r, displayLunarOrHoliday)
            views.setTextViewText(R.id.tv_range_right_r, rangeStr)
            views.setTextViewTextSize(R.id.tv_weather_left_r, TypedValue.COMPLEX_UNIT_SP, 12f * textScale)
            views.setTextViewTextSize(R.id.tv_solar_left_r, TypedValue.COMPLEX_UNIT_SP, 11.5f * textScale)
            views.setTextViewTextSize(R.id.tv_lunar_left_r, TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
            views.setTextViewTextSize(R.id.tv_range_right_r, TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
            views.setTextColor(R.id.tv_weather_left_r, mainColor)
            views.setTextColor(R.id.tv_solar_left_r, mainColor)
            views.setTextColor(R.id.tv_lunar_left_r, accentColor)
            views.setTextColor(R.id.tv_range_right_r, mainColor)

            val weekCal = Calendar.getInstance()
            val currentDow = weekCal.get(Calendar.DAY_OF_WEEK)
            val offsetToMon = if (currentDow == Calendar.SUNDAY) -6 else Calendar.MONDAY - currentDow
            val todayIndexInWeek = if (currentDow == Calendar.SUNDAY) 6 else currentDow - Calendar.MONDAY
            weekCal.add(Calendar.DAY_OF_MONTH, offsetToMon)

            val dayIds = intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
            val labels = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

            for (i in 0..6) {
                val d = weekCal.get(Calendar.DAY_OF_MONTH)
                val m = weekCal.get(Calendar.MONTH) + 1
                val y = weekCal.get(Calendar.YEAR)
                val lunar = LunarCalendar.getLunarInfo(d, m, y)

                val nextCal = (weekCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val nextLunar = LunarCalendar.getLunarInfo(nextCal.get(Calendar.DAY_OF_MONTH), nextCal.get(Calendar.MONTH) + 1, nextCal.get(Calendar.YEAR))
                val isGoldDotDay = (lunar.first == 14 || lunar.first == 15 || lunar.first == 1 || nextLunar.first == 1)

                val dayHoliday = LunarCalendar.getVietnameseHoliday(d, m, y)
                val isMajorHoliday = dayHoliday.themeType == "TET" || dayHoliday.themeType == "NATIONAL"

                val baseText = "${labels[i]}\n$d\n${lunar.first}/${lunar.second}"
                if (isGoldDotDay) {
                    val fullText = "$baseText●"
                    val spannable = SpannableString(fullText)
                    val dotStart = fullText.length - 1
                    spannable.setSpan(ForegroundColorSpan(Color.parseColor("#FDE047")), dotStart, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(RelativeSizeSpan(1.35f), dotStart, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    views.setTextViewText(dayIds[i], spannable)
                } else {
                    views.setTextViewText(dayIds[i], baseText)
                }

                views.setTextViewTextSize(dayIds[i], TypedValue.COMPLEX_UNIT_SP, 10.5f * textScale)

                if (i == todayIndexInWeek) {
                    views.setInt(dayIds[i], "setBackgroundResource", R.drawable.bg_glass_pill)
                    views.setTextColor(dayIds[i], accentColor)
                } else {
                    views.setInt(dayIds[i], "setBackgroundResource", 0)
                    val isRedDay = (i == 6) || isMajorHoliday
                    views.setTextColor(dayIds[i], if (isRedDay) Color.parseColor("#FF8A80") else mainColor)
                }
                weekCal.add(Calendar.DAY_OF_MONTH, 1)
            }

            val fcIds = intArrayOf(R.id.fc_0, R.id.fc_1, R.id.fc_2, R.id.fc_3, R.id.fc_4, R.id.fc_5, R.id.fc_6)
            for (i in 0..6) {
                val colHeader = if (i == todayIndexInWeek) "Nay" else labels[i]
                val forecastOffset = i - todayIndexInWeek
                if (forecastOffset >= 0) {
                    val raw = prefs.getString("fc_$forecastOffset", "⛅\n--°\n--°")
                    views.setTextViewText(fcIds[i], "$colHeader\n$raw")
                } else {
                    views.setTextViewText(fcIds[i], "$colHeader\n·\n--°\n--°")
                }
                views.setTextViewTextSize(fcIds[i], TypedValue.COMPLEX_UNIT_SP, 10.5f * textScale)
                views.setTextColor(fcIds[i], if (i == todayIndexInWeek) accentColor else mainColor)
            }

            val playEffectIntent = Intent(context, LiquidWidgetProvider::class.java).apply {
                action = "com.custom.liquidwidget.PLAY_EFFECT"
            }
            val playEffectPi = PendingIntent.getBroadcast(context, 14, playEffectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val settingsPi = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val clockPi = buildClockPendingIntent(context)
            val calPi = buildCalendarPendingIntent(context)
            val weatherPi = buildWeatherPendingIntent(context)

            views.setOnClickPendingIntent(R.id.widget_root, playEffectPi)
            views.setOnClickPendingIntent(R.id.tv_greeting, settingsPi)
            views.setOnClickPendingIntent(R.id.zone_clock_left, clockPi)
            views.setOnClickPendingIntent(R.id.zone_clock_center, clockPi)
            views.setOnClickPendingIntent(R.id.zone_clock_right, clockPi)
            views.setOnClickPendingIntent(R.id.zone_calendar_left, calPi)
            views.setOnClickPendingIntent(R.id.zone_week_calendar, calPi)
            views.setOnClickPendingIntent(R.id.tv_sub_center, calPi)
            views.setOnClickPendingIntent(R.id.zone_info_left_in_right_mode, calPi)
            views.setOnClickPendingIntent(R.id.zone_weather_right, weatherPi)
            views.setOnClickPendingIntent(R.id.zone_forecast_weather, weatherPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun detectWeatherEffect(desc: String, isNight: Boolean = false): String = when {
            desc.contains("Giông") || desc.contains("bão") -> "THUNDER"
            desc.contains("mưa") || desc.contains("Mưa") -> "RAIN"
            desc.contains("quang") || desc.contains("nắng") -> if (isNight) "MOON" else "SUN"
            desc.contains("Ít mây") || desc.contains("Có mây") -> if (isNight) "MOON_CLOUD" else "SUN_CLOUD"
            desc.contains("mây") || desc.contains("Sương") -> if (isNight) "MOON_CLOUD" else "CLOUD"
            else -> if (isNight) "MOON" else "NONE"
        }

        fun loadCustomTypeface(context: Context, fontType: String): Typeface {
            if (fontType == "CUSTOM") {
                val customFile = File(context.filesDir, "custom_font.ttf")
                if (customFile.exists() && customFile.length() > 100) {
                    try {
                        val tf = Typeface.createFromFile(customFile)
                        if (tf != null) return tf
                    } catch (_: Exception) {}
                }
            }
            return when (fontType) {
                "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                "MONO" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                "LIGHT" -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
                "ITALIC" -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                else -> Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
        }

        fun drawTextBitmap(context: Context, text: String, spSize: Float, color: Int, tf: Typeface): Bitmap {
            val density = context.resources.displayMetrics.density
            val pxSize = spSize * density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = pxSize
                this.color = color
                typeface = tf
                setShadowLayer(4f * density, 0f, 1.5f * density, Color.argb(165, 0, 0, 0))
            }
            val width = (paint.measureText(text) + 12 * density).toInt().coerceAtLeast(10)
            val fm = paint.fontMetrics
            val height = (fm.descent - fm.ascent + 4 * density).toInt().coerceAtLeast(10)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawText(text, 2 * density, -fm.ascent + 1 * density, paint)
            return bmp
        }

        fun drawLiquidGlassBitmap(
            hexColor: String,
            alphaPercent: Int,
            radius: Float,
            effectType: String = "NONE",
            isNight: Boolean = false,
            lunarDay: Int = 15
        ): Bitmap {
            val w = 600
            val h = 320
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val rect = RectF(2f, 2f, w - 2f, h - 2f)

            val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)

            val baseColor = Color.parseColor(hexColor)
            val alpha = (alphaPercent * 255 / 100).coerceIn(0, 255)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            }
            canvas.drawRect(rect, fillPaint)

            drawArtisticOverlay(canvas, w.toFloat(), h.toFloat(), effectType, isNight, lunarDay)

            val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(Color.argb(75, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.argb(45, 255, 255, 255)),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, sheenPaint)
            canvas.restore()

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(70, 255, 255, 255), Color.argb(180, 255, 255, 255)),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            return bmp
        }

        private fun drawFivePetalBlossom(canvas: Canvas, cx: Float, cy: Float, r: Float, petalColor: Int, centerColor: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = petalColor
            for (i in 0 until 5) {
                val angle = Math.toRadians((i * 72 - 18).toDouble())
                val px = cx + (Math.cos(angle) * r * 0.58f).toFloat()
                val py = cy + (Math.sin(angle) * r * 0.58f).toFloat()
                canvas.drawCircle(px, py, r * 0.48f, p)
            }
            p.color = centerColor
            canvas.drawCircle(cx, cy, r * 0.25f, p)
        }

        private fun drawTetLantern3D(canvas: Canvas, lx: Float, ly: Float, scale: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(lx, ly, 44f * scale, intArrayOf(Color.argb(135, 253, 224, 71), Color.argb(65, 239, 68, 68), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(lx, ly, 44f * scale, p)
            p.shader = null
            p.strokeWidth = 2f * scale
            p.color = Color.rgb(253, 224, 71)
            canvas.drawLine(lx, ly - 32f * scale, lx, ly - 16f * scale, p)
            val bodyRect = RectF(lx - 17f * scale, ly - 15f * scale, lx + 17f * scale, ly + 15f * scale)
            p.shader = RadialGradient(lx - 5f * scale, ly - 4f * scale, 22f * scale, intArrayOf(Color.rgb(254, 202, 202), Color.rgb(220, 38, 38), Color.rgb(127, 29, 29)), null, Shader.TileMode.CLAMP)
            canvas.drawOval(bodyRect, p)
            p.shader = null
            p.color = Color.rgb(250, 204, 21)
            canvas.drawRoundRect(RectF(lx - 9f * scale, ly - 18f * scale, lx + 9f * scale, ly - 13f * scale), 3f, 3f, p)
            canvas.drawRoundRect(RectF(lx - 9f * scale, ly + 13f * scale, lx + 9f * scale, ly + 18f * scale), 3f, 3f, p)
            p.strokeWidth = 1.6f * scale
            canvas.drawLine(lx, ly + 18f * scale, lx, ly + 30f * scale, p)
        }

        private fun drawVietnameseTetScene3D(canvas: Canvas, w: Float, h: Float, rnd: Random) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.argb(155, 153, 27, 27), Color.argb(125, 185, 28, 28), Color.argb(115, 120, 53, 15)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 3.5f
            p.color = Color.rgb(92, 45, 22)
            val branch = Path().apply {
                moveTo(w * 0.96f, h * 0.72f)
                quadTo(w * 0.82f, h * 0.56f, w * 0.68f, h * 0.48f)
                moveTo(w * 0.85f, h * 0.60f)
                quadTo(w * 0.80f, h * 0.44f, w * 0.73f, h * 0.36f)
            }
            canvas.drawPath(branch, p)
            p.style = Paint.Style.FILL

            drawFivePetalBlossom(canvas, w * 0.68f, h * 0.48f, 9.5f, Color.rgb(253, 224, 71), Color.rgb(217, 119, 6))
            drawFivePetalBlossom(canvas, w * 0.73f, h * 0.36f, 8.5f, Color.rgb(254, 240, 138), Color.rgb(217, 119, 6))
            drawFivePetalBlossom(canvas, w * 0.82f, h * 0.46f, 9.0f, Color.rgb(253, 164, 175), Color.rgb(225, 29, 72))
            drawTetLantern3D(canvas, w * 0.60f, h * 0.14f, 0.82f)
            drawTetLantern3D(canvas, w * 0.86f, h * 0.13f, 0.72f)
            drawRealisticFirework(canvas, w * 0.66f, h * 0.34f, 52f, Color.argb(240, 253, 224, 71), Color.argb(220, 25
