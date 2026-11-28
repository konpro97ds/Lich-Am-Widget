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
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleWeatherUpdate(context)
        schedulePeriodicRefresh(context)
    }

    companion object {
        private fun buildClockPendingIntent(context: Context): PendingIntent {
            val clockPackages = listOf(
                "com.coloros.alarmclock",
                "com.oplus.alarmclock",
                "com.oneplus.deskclock",
                "com.google.android.deskclock",
                "com.android.deskclock",
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
            val calPackages = listOf(
                "com.coloros.calendar",
                "com.oplus.calendar",
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.android.calendar"
            )
            for (pkg in calPackages) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return PendingIntent.getActivity(context, 12, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
            val catIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (catIntent.resolveActivity(context.packageManager) != null) {
                return PendingIntent.getActivity(context, 12, catIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
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
                "com.coloros.weather",
                "com.oplus.weather",
                "com.heytap.weather",
                "com.google.android.apps.weather",
                "com.miui.weather2",
                "com.sec.android.daemonapp"
            )
            for (pkg in weatherPackages) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return PendingIntent.getActivity(context, 13, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
            val catIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_WEATHER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (catIntent.resolveActivity(context.packageManager) != null) {
                return PendingIntent.getActivity(context, 13, catIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=thoi+tiet")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(context, 13, webIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_liquid)
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

            val opts = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(240)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150).coerceAtLeast(120)
            val aspectRatio = (minW.toFloat() / minH.toFloat()).coerceIn(1.6f, 2.8f)

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
                drawLiquidGlassBitmap(bgColorHex, bgAlpha, 52f, activeEffect, isNight, lunarDay, aspectRatio)
            )
            views.setViewVisibility(R.id.pb_glass_shimmer, if (enableEffects) View.VISIBLE else View.GONE)

            val showRain = enableEffects && (activeEffect == "RAIN" || activeEffect == "THUNDER")
            val showSun = enableEffects && (activeEffect == "SUN" || activeEffect == "SUN_CLOUD")
            val showMoon = enableEffects && (activeEffect == "MOON" || activeEffect == "MOON_CLOUD" || activeEffect == "MID_AUTUMN")
            val showCloud = enableEffects && (activeEffect == "CLOUD" || activeEffect == "SUN_CLOUD" || activeEffect == "MOON_CLOUD" || activeEffect == "RAIN" || activeEffect == "THUNDER")
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
            val solarStr = "$dayOfWeek, $dd Thg $mm, $yy"
            val baseLunarStr = lunarToday.third
            val upcomingStr = LunarCalendar.getNextUpcomingHoliday(dd, mm, yy)
            val displayLunarOrHoliday = when {
                holiday.name.isNotEmpty() -> "${holiday.name} · $baseLunarStr"
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

            views.setTextViewText(R.id.tv_sub_center, "$solarStr · $displayLunarOrHoliday · $tempStr $descStr")
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
            lunarDay: Int = 15,
            aspectRatio: Float = 2.15f
        ): Bitmap {
            val h = 340
            val w = (h * aspectRatio).toInt().coerceIn(580, 920)
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
                    intArrayOf(Color.argb(65, 255, 255, 255), Color.argb(8, 255, 255, 255), Color.argb(38, 255, 255, 255)),
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
                    intArrayOf(Color.argb(225, 255, 255, 255), Color.argb(65, 255, 255, 255), Color.argb(175, 255, 255, 255)),
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
            p.shader = RadialGradient(lx, ly, 42f * scale, intArrayOf(Color.argb(125, 253, 224, 71), Color.argb(55, 239, 68, 68), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(lx, ly, 42f * scale, p)
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
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.argb(150, 153, 27, 27), Color.argb(120, 185, 28, 28), Color.argb(110, 120, 53, 15)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 3.8f
            p.color = Color.rgb(92, 45, 22)
            val rightBranch = Path().apply {
                moveTo(w * 0.98f, h * 0.68f)
                quadTo(w * 0.84f, h * 0.52f, w * 0.70f, h * 0.44f)
                moveTo(w * 0.87f, h * 0.56f)
                quadTo(w * 0.81f, h * 0.40f, w * 0.75f, h * 0.30f)
            }
            val leftBranch = Path().apply {
                moveTo(w * 0.02f, h * 0.62f)
                quadTo(w * 0.14f, h * 0.50f, w * 0.25f, h * 0.44f)
            }
            canvas.drawPath(rightBranch, p)
            canvas.drawPath(leftBranch, p)
            p.style = Paint.Style.FILL

            drawFivePetalBlossom(canvas, w * 0.70f, h * 0.44f, 10.5f, Color.rgb(253, 224, 71), Color.rgb(217, 119, 6))
            drawFivePetalBlossom(canvas, w * 0.75f, h * 0.30f, 9.5f, Color.rgb(254, 240, 138), Color.rgb(217, 119, 6))
            drawFivePetalBlossom(canvas, w * 0.84f, h * 0.46f, 10.0f, Color.rgb(250, 204, 21), Color.rgb(180, 83, 9))
            drawFivePetalBlossom(canvas, w * 0.25f, h * 0.44f, 9.5f, Color.rgb(253, 164, 175), Color.rgb(225, 29, 72))
            drawFivePetalBlossom(canvas, w * 0.15f, h * 0.50f, 8.5f, Color.rgb(254, 205, 211), Color.rgb(225, 29, 72))

            drawTetLantern3D(canvas, w * 0.56f, h * 0.15f, 0.82f)
            drawTetLantern3D(canvas, w * 0.88f, h * 0.14f, 0.75f)

            val lx = w * 0.64f
            val ly = h * 0.22f
            p.color = Color.rgb(220, 38, 38)
            canvas.drawRoundRect(RectF(lx - 8f, ly - 12f, lx + 8f, ly + 12f), 3f, 3f, p)
            p.color = Color.rgb(253, 224, 71)
            canvas.drawCircle(lx, ly - 2f, 3.2f, p)

            drawRealisticFirework(canvas, w * 0.68f, h * 0.34f, 48f, Color.argb(235, 253, 224, 71), Color.argb(215, 251, 146, 60))
            drawRealisticFirework(canvas, w * 0.44f, h * 0.26f, 38f, Color.argb(230, 251, 113, 133), Color.argb(210, 253, 224, 71))

            for (i in 0..24) {
                p.color = if (i % 2 == 0) Color.argb(225, 253, 224, 71) else Color.argb(215, 253, 164, 175)
                val cx = rnd.nextFloat() * w
                val cy = rnd.nextFloat() * h
                canvas.save()
                canvas.rotate(rnd.nextFloat() * 180f, cx, cy)
                canvas.drawOval(RectF(cx - 5f, cy - 3f, cx + 5f, cy + 3f), p)
                canvas.restore()
            }
        }

        private fun drawVietnamFlagCelebration3D(canvas: Canvas, w: Float, h: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.argb(145, 153, 27, 27), Color.argb(95, 127, 29, 29), Color.argb(80, 15, 23, 42)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null
            drawRealisticFirework(canvas, w * 0.44f, h * 0.28f, 44f, Color.argb(235, 253, 224, 71), Color.argb(215, 251, 146, 60))

            val fx = w * 0.56f
            val fy = h * 0.22f
            val fw = 126f
            val fh = 84f
            val flagPath = Path().apply {
                moveTo(fx, fy)
                cubicTo(fx + fw * 0.35f, fy - 10f, fx + fw * 0.65f, fy + 10f, fx + fw, fy - 2f)
                lineTo(fx + fw, fy + fh - 2f)
                cubicTo(fx + fw * 0.65f, fy + fh + 10f, fx + fw * 0.35f, fy + fh - 10f, fx, fy + fh)
                close()
            }
            p.shader = LinearGradient(fx, fy, fx + fw, fy + fh * 0.3f, intArrayOf(Color.rgb(220, 38, 38), Color.rgb(248, 113, 113), Color.rgb(185, 28, 28)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(flagPath, p)
            p.shader = null

            val starCx = fx + fw * 0.50f
            val starCy = fy + fh * 0.50f
            val starPath = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 23f else 8.8f
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val sx = starCx + (Math.cos(angle) * r).toFloat()
                val sy = starCy + (Math.sin(angle) * r).toFloat()
                if (i == 0) starPath.moveTo(sx, sy) else starPath.lineTo(sx, sy)
            }
            starPath.close()
            p.color = Color.rgb(250, 204, 21)
            canvas.drawPath(starPath, p)
        }

        private fun drawMidAutumnScene3D(canvas: Canvas, w: Float, h: Float, rnd: Random) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(120, 15, 23, 42), Color.argb(95, 30, 27, 75), Color.argb(75, 49, 16, 66)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            drawRealisticLunarMoon3D(canvas, w * 0.72f, h * 0.34f, 34f, 15, 1.0f)

            for (i in 0..24) {
                p.color = Color.argb(130 + rnd.nextInt(125), 254, 249, 195)
                canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * (h * 0.72f), 0.9f + rnd.nextFloat() * 1.5f, p)
            }

            drawTetLantern3D(canvas, w * 0.87f, h * 0.16f, 0.72f)
            drawRealisticCloud3D(canvas, w * 0.63f, h * 0.44f, 0.88f, 0.55f)
        }

        private fun drawRealisticLunarMoon3D(canvas: Canvas, cx: Float, cy: Float, r: Float, lunarDay: Int, alphaMult: Float = 1f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val isFullMoon = lunarDay in 12..18
            val isHalfMoon = (lunarDay in 7..11) || (lunarDay in 19..23)

            p.shader = RadialGradient(cx, cy, r * 2.6f, intArrayOf(Color.argb((95 * alphaMult).toInt(), 254, 249, 195), Color.argb((35 * alphaMult).toInt(), 253, 224, 71), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 2.6f, p)
            p.shader = null

            val saveCount = canvas.saveLayer(cx - r * 1.5f, cy - r * 1.5f, cx + r * 1.5f, cy + r * 1.5f, null)
            p.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r * 1.25f, intArrayOf(Color.argb((250 * alphaMult).toInt(), 255, 255, 255), Color.argb((235 * alphaMult).toInt(), 254, 249, 195), Color.argb((205 * alphaMult).toInt(), 253, 224, 71)), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, p)
            p.shader = null

            p.color = Color.argb((38 * alphaMult).toInt(), 148, 163, 184)
            canvas.drawCircle(cx - r * 0.28f, cy - r * 0.22f, r * 0.32f, p)
            canvas.drawCircle(cx + r * 0.22f, cy - r * 0.12f, r * 0.24f, p)
            canvas.drawCircle(cx - r * 0.08f, cy + r * 0.30f, r * 0.22f, p)

            if (!isFullMoon) {
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    color = Color.BLACK
                }
                val isWaxing = lunarDay < 15
                val offsetFactor = if (isHalfMoon) 0.58f else 0.36f
                val shadowCx = if (isWaxing) cx - r * offsetFactor else cx + r * offsetFactor
                canvas.drawCircle(shadowCx, cy - r * 0.08f, r * 0.96f, clearPaint)
            }
            canvas.restoreToCount(saveCount)
        }

        private fun drawRealisticCloud3D(canvas: Canvas, cx: Float, cy: Float, scale: Float, alphaMult: Float = 1f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val basePath = Path().apply {
                addRoundRect(RectF(cx - 58f * scale, cy - 2f * scale, cx + 58f * scale, cy + 24f * scale), 15f * scale, 15f * scale, Path.Direction.CW)
                addCircle(cx - 34f * scale, cy + 2f * scale, 21f * scale, Path.Direction.CW)
                addCircle(cx - 10f * scale, cy - 12f * scale, 29f * scale, Path.Direction.CW)
                addCircle(cx + 18f * scale, cy - 8f * scale, 25f * scale, Path.Direction.CW)
                addCircle(cx + 38f * scale, cy + 3f * scale, 19f * scale, Path.Direction.CW)
            }
            canvas.save()
            canvas.translate(0f, 3.5f * scale)
            p.color = Color.argb((75 * alphaMult).toInt(), 30, 41, 59)
            canvas.drawPath(basePath, p)
            canvas.restore()

            p.shader = LinearGradient(
                cx, cy - 40f * scale, cx, cy + 24f * scale,
                intArrayOf(
                    Color.argb((225 * alphaMult).toInt(), 255, 255, 255),
                    Color.argb((175 * alphaMult).toInt(), 241, 245, 249),
                    Color.argb((115 * alphaMult).toInt(), 148, 163, 184)
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(basePath, p)
            p.shader = null

            p.shader = RadialGradient(cx - 10f * scale, cy - 18f * scale, 26f * scale, intArrayOf(Color.argb((140 * alphaMult).toInt(), 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx - 10f * scale, cy - 14f * scale, 24f * scale, p)
            p.shader = null
        }

        private fun drawStormCloud3D(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val cloudPath = Path().apply {
                addRoundRect(RectF(cx - 62f * scale, cy - 2f * scale, cx + 62f * scale, cy + 25f * scale), 16f * scale, 16f * scale, Path.Direction.CW)
                addCircle(cx - 35f * scale, cy + 2f * scale, 23f * scale, Path.Direction.CW)
                addCircle(cx - 8f * scale, cy - 12f * scale, 31f * scale, Path.Direction.CW)
                addCircle(cx + 21f * scale, cy - 7f * scale, 26f * scale, Path.Direction.CW)
                addCircle(cx + 42f * scale, cy + 3f * scale, 20f * scale, Path.Direction.CW)
            }
            p.shader = LinearGradient(cx, cy - 42f * scale, cx, cy + 25f * scale, intArrayOf(Color.argb(210, 203, 213, 225), Color.argb(200, 51, 65, 85), Color.argb(230, 15, 23, 42)), null, Shader.TileMode.CLAMP)
            canvas.drawPath(cloudPath, p)
        }

        private fun drawRealisticFirework(canvas: Canvas, fx: Float, fy: Float, radius: Float, primaryColor: Int, secondaryColor: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(fx, fy, radius * 1.25f, intArrayOf(Color.argb(175, 255, 255, 255), Color.argb(105, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(fx, fy, radius * 1.25f, p)
            p.shader = null
            p.strokeCap = Paint.Cap.ROUND
            for (a in 0 until 360 step 22) {
                val rad = Math.toRadians(a.toDouble())
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                val innerR = radius * 0.16f
                val outerR = if (a % 44 == 0) radius else radius * 0.72f
                p.style = Paint.Style.STROKE
                p.strokeWidth = if (a % 44 == 0) 2.4f else 1.6f
                p.color = if (a % 44 == 0) primaryColor else secondaryColor
                canvas.drawLine(fx + cosA * innerR, fy + sinA * innerR, fx + cosA * outerR, fy + sinA * outerR, p)
                p.style = Paint.Style.FILL
                p.color = Color.argb(225, 255, 255, 255)
                canvas.drawCircle(fx + cosA * (outerR + 3f), fy + sinA * (outerR + 3f), 1.9f, p)
            }
        }

        private fun drawRefractiveWaterDrop(canvas: Canvas, cx: Float, cy: Float, r: Float) {
            val smallR = r * 0.68f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(cx + smallR * 0.2f, cy + smallR * 0.2f, smallR, intArrayOf(Color.argb(15, 255, 255, 255), Color.argb(45, 224, 242, 254), Color.argb(75, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 0.85f, 1.0f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, smallR, p)
            p.shader = null
            p.color = Color.argb(185, 255, 255, 255)
            canvas.drawCircle(cx - smallR * 0.32f, cy - smallR * 0.32f, smallR * 0.24f, p)
        }

        private fun drawRealisticChristmasScene3D(canvas: Canvas, w: Float, h: Float, rnd: Random) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.argb(115, 6, 78, 59), Color.argb(85, 15, 23, 42), Color.argb(95, 49, 16, 66)), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            p.shader = null

            val tx = w * 0.773f
            val baseTy = h * 0.62f
            p.color = Color.argb(210, 92, 51, 23)
            canvas.drawRoundRect(RectF(tx - 7f, baseTy - 4f, tx + 7f, baseTy + 14f), 3f, 3f, p)

            val tiers = listOf(Triple(baseTy - 14f, 44f, 32f), Triple(baseTy - 32f, 36f, 28f), Triple(baseTy - 48f, 28f, 25f), Triple(baseTy - 63f, 20f, 22f))
            for ((ty, halfW, tierH) in tiers) {
                val treePath = Path().apply {
                    moveTo(tx, ty - tierH)
                    lineTo(tx - halfW, ty)
                    quadTo(tx, ty + 6f, tx + halfW, ty)
                    close()
                }
                p.shader = LinearGradient(tx - halfW, ty - tierH, tx + halfW, ty, intArrayOf(Color.argb(225, 52, 211, 153), Color.argb(235, 6, 95, 70), Color.argb(240, 4, 47, 46)), null, Shader.TileMode.CLAMP)
                canvas.drawPath(treePath, p)
                p.shader = null
            }

            val starY = baseTy - 87f
            val starPath = Path()
            for (i in 0 until 10) {
                val rStar = if (i % 2 == 0) 9.5f else 4.2f
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val sx = tx + (Math.cos(angle) * rStar).toFloat()
                val sy = starY + (Math.sin(angle) * rStar).toFloat()
                if (i == 0) starPath.moveTo(sx, sy) else starPath.lineTo(sx, sy)
            }
            starPath.close()
            p.color = Color.rgb(254, 240, 138)
            canvas.drawPath(starPath, p)

            for (i in 0..38) {
                val sx = rnd.nextFloat() * w
                val sy = rnd.nextFloat() * h
                val sr = 1.4f + rnd.nextFloat() * 3.0f
                p.color = Color.argb(200, 255, 255, 255)
                canvas.drawCircle(sx, sy, sr, p)
            }
        }

        private fun drawArtisticOverlay(canvas: Canvas, w: Float, h: Float, effect: String, isNight: Boolean, lunarDay: Int) {
            val rnd = Random(System.currentTimeMillis() / 60000L)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            when (effect) {
                "TET" -> drawVietnameseTetScene3D(canvas, w, h, rnd)
                "NATIONAL" -> drawVietnamFlagCelebration3D(canvas, w, h)
                "MID_AUTUMN" -> drawMidAutumnScene3D(canvas, w, h, rnd)
                "XMAS" -> drawRealisticChristmasScene3D(canvas, w, h, rnd)
                "SUN", "SUN_CLOUD" -> {
                    val isSoftSun = (effect == "SUN_CLOUD")
                    val sx = w * 0.72f
                    val sy = h * 0.34f
                    val sunR = if (isSoftSun) 24f else 29f
                    val glowR = if (isSoftSun) 115f else 165f
                    val glowAlpha1 = if (isSoftSun) 68 else 110
                    val glowAlpha2 = if (isSoftSun) 24 else 42

                    p.shader = RadialGradient(sx, sy, glowR, intArrayOf(Color.argb(glowAlpha1, 253, 224, 71), Color.argb(glowAlpha2, 245, 158, 11), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                    canvas.drawCircle(sx, sy, glowR, p)
                    p.shader = null

                    p.shader = RadialGradient(sx - sunR * 0.25f, sy - sunR * 0.25f, sunR * 1.2f, intArrayOf(Color.argb(235, 254, 249, 195), Color.argb(210, 253, 224, 71), Color.argb(185, 245, 158, 11)), null, Shader.TileMode.CLAMP)
                    canvas.drawCircle(sx, sy, sunR, p)
                    p.shader = null

                    if (isSoftSun) {
                        drawRealisticCloud3D(canvas, w * 0.62f, h * 0.42f, 0.96f, 0.95f)
                        drawRealisticCloud3D(canvas, w * 0.82f, h * 0.45f, 0.78f, 0.88f)
                    }
                }
                "MOON", "MOON_CLOUD" -> {
                    p.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(85, 9, 13, 28), Color.argb(35, 30, 27, 75), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, p)
                    p.shader = null
                    val moonAlpha = if (effect == "MOON_CLOUD") 0.65f else 1.0f
                    drawRealisticLunarMoon3D(canvas, w * 0.72f, h * 0.34f, 28f, lunarDay, moonAlpha)
                    for (i in 0..26) {
                        p.color = Color.argb((120 + rnd.nextInt(135)), 255, 255, 255)
                        canvas.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * (h * 0.75f), 0.8f + rnd.nextFloat() * 1.6f, p)
                    }
                    if (effect == "MOON_CLOUD") {
                        drawRealisticCloud3D(canvas, w * 0.62f, h * 0.41f, 0.92f, 0.85f)
                        drawRealisticCloud3D(canvas, w * 0.80f, h * 0.44f, 0.78f, 0.78f)
                    }
                }
                "RAIN" -> {
                    p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.argb(115, 51, 65, 85), Color.argb(85, 71, 85, 105), Color.argb(65, 30, 41, 59)), null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, p)
                    p.shader = null
                    drawRealisticCloud3D(canvas, w * 0.38f, h * 0.28f, 1.12f, 0.78f)
                    drawRealisticCloud3D(canvas, w * 0.68f, h * 0.30f, 1.22f, 0.84f)
                    drawRealisticCloud3D(canvas, w * 0.88f, h * 0.34f, 0.92f, 0.72f)
                    for (i in 0..14) {
                        drawRefractiveWaterDrop(canvas, 24f + rnd.nextFloat() * (w - 48f), 24f + rnd.nextFloat() * (h - 48f), 1.6f + rnd.nextFloat() * 2.2f)
                    }
                }
                "THUNDER" -> {
                    p.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(135, 9, 13, 22), Color.argb(75, 30, 27, 75), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, p)
                    p.shader = null
                    if (isNight) drawRealisticLunarMoon3D(canvas, w * 0.78f, h * 0.28f, 22f, lunarDay, 0.30f)
                    drawStormCloud3D(canvas, w * 0.56f, h * 0.30f, 1.15f)
                    drawStormCloud3D(canvas, w * 0.78f, h * 0.32f, 1.05f)
                    for (i in 0..16) {
                        drawRefractiveWaterDrop(canvas, 20f + rnd.nextFloat() * (w - 40f), 20f + rnd.nextFloat() * (h - 40f), 2.4f + rnd.nextFloat() * 3.4f)
                    }
                }
                "CLOUD" -> {
                    if (isNight) drawRealisticLunarMoon3D(canvas, w * 0.72f, h * 0.32f, 25f, lunarDay, 0.45f)
                    drawRealisticCloud3D(canvas, w * 0.38f, h * 0.36f, 0.92f, 0.88f)
                    drawRealisticCloud3D(canvas, w * 0.62f, h * 0.34f, 1.18f, 1.0f)
                    drawRealisticCloud3D(canvas, w * 0.82f, h * 0.38f, 0.88f, 0.88f)
                }
            }
        }

        fun scheduleWeatherUpdate(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            val periodic = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork("WeatherSync", ExistingPeriodicWorkPolicy.KEEP, periodic)
        }

        fun schedulePeriodicRefresh(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, LiquidWidgetProvider::class.java).apply {
                action = "com.custom.liquidwidget.TICK_CLOCK"
            }
            val pi = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val nextRefresh = System.currentTimeMillis() + 15 * 60 * 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefresh, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefresh, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextRefresh, pi)
            }
        }
    }
}
