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

            val weatherEff = detectWeatherEffect(descStr, isNight)
            val isRealRain = (weatherEff == "RAIN" || weatherEff == "THUNDER")

            val activeEffect = if (!enableEffects) "NONE"
            else if (testEffectMode != "AUTO") testEffectMode
            else if (holiday.themeType != "NONE") holiday.themeType
            else weatherEff

            // Vẽ nền Kính Lỏng kết hợp ảnh 3D PNG/WebP render sẵn từ res/drawable-nodpi/
            views.setImageViewBitmap(
                R.id.iv_glass_bg,
                drawLiquidGlassBitmap(context, bgColorHex, bgAlpha, 52f, activeEffect, lunarDay, aspectRatio, isRealRain)
            )
            views.setViewVisibility(R.id.pb_glass_shimmer, if (enableEffects) View.VISIBLE else View.GONE)

            // Chỉ giữ lại các hạt động tinh tế (mưa, chớp, hoa rơi, tuyết) bổ trợ cho ảnh 3D
            val showRain = enableEffects && (activeEffect == "RAIN" || activeEffect == "THUNDER" || ((activeEffect == "TET" || activeEffect == "NATIONAL" || activeEffect == "MID_AUTUMN") && isRealRain))
            val showThunder = enableEffects && (activeEffect == "THUNDER" || ((activeEffect == "TET" || activeEffect == "NATIONAL") && weatherEff == "THUNDER"))
            val showTet = enableEffects && (activeEffect == "TET" || activeEffect == "NATIONAL")
            val showMidAutumn = enableEffects && (activeEffect == "MID_AUTUMN")
            val showXmas = enableEffects && (activeEffect == "XMAS")
            val showMoonStars = enableEffects && (activeEffect == "MOON" || activeEffect == "MOON_CLOUD")

            views.setViewVisibility(R.id.pb_weather_rain, if (showRain) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_thunder, if (showThunder) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_tet, if (showTet) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_mid_autumn, if (showMidAutumn) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_xmas, if (showXmas) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_moon, if (showMoonStars) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.pb_weather_sun, View.GONE)
            views.setViewVisibility(R.id.pb_weather_cloud, View.GONE)

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
                setShadowLayer(4f * density, 0f, 1.5f * density, Color.argb(175, 0, 0, 0))
            }
            val width = (paint.measureText(text) + 12 * density).toInt().coerceAtLeast(10)
            val fm = paint.fontMetrics
            val height = (fm.descent - fm.ascent + 4 * density).toInt().coerceAtLeast(10)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawText(text, 2 * density, -fm.ascent + 1 * density, paint)
            return bmp
        }

        /**
         * Nạp file ảnh 3D PNG/WebP từ res/drawable-nodpi/ theo tên tài nguyên
         * Tự động tìm kiếm động (getIdentifier) nên không bao giờ lỗi biên dịch dù thiếu file nào
         */
        private fun load3DArtBitmap(context: Context, resName: String): Bitmap? {
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId == 0) return null
            return try {
                BitmapFactory.decodeResource(context.resources, resId)
            } catch (_: Exception) {
                null
            }
        }

        private fun resolveArtResourceName(effectType: String, lunarDay: Int): String? {
            return when (effectType) {
                "SUN" -> "art_sun"
                "SUN_CLOUD" -> "art_sun_cloud"
                "CLOUD" -> "art_cloud"
                "RAIN" -> "art_rain"
                "THUNDER" -> "art_thunder"
                "TET" -> "art_tet"
                "NATIONAL" -> "art_national"
                "MID_AUTUMN" -> "art_mid_autumn"
                "XMAS" -> "art_xmas"
                "MOON" -> when (lunarDay.coerceIn(1, 30)) {
                    in 12..18 -> "art_moon_full"
                    in 7..11, in 19..23 -> "art_moon_half"
                    else -> "art_moon_crescent"
                }
                "MOON_CLOUD" -> "art_moon_cloud"
                else -> null
            }
        }

        /**
         * Dựng nền Kính Lỏng (Liquid Glass) cao cấp + ghép ảnh 3D render sẵn vào đúng vị trí
         */
        fun drawLiquidGlassBitmap(
            context: Context,
            hexColor: String,
            alphaPercent: Int,
            radius: Float,
            effectType: String = "NONE",
            lunarDay: Int = 15,
            aspectRatio: Float = 2.15f,
            isRealRain: Boolean = false
        ): Bitmap {
            val h = 360
            val w = (h * aspectRatio).toInt().coerceIn(600, 960)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val rect = RectF(2f, 2f, w - 2f, h - 2f)

            val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clipPath)

            // 1. Lớp nền kính chuyển sắc sâu (Liquid Glass Base)
            val baseColor = Color.parseColor(hexColor)
            val alpha = (alphaPercent * 255 / 100).coerceIn(0, 255)
            val r = Color.red(baseColor)
            val g = Color.green(baseColor)
            val b = Color.blue(baseColor)

            val glassBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(
                        Color.argb((alpha * 1.12f).toInt().coerceAtMost(255), (r + 18).coerceAtMost(255), (g + 22).coerceAtMost(255), (b + 30).coerceAtMost(255)),
                        Color.argb(alpha, r, g, b),
                        Color.argb((alpha * 1.15f).toInt().coerceAtMost(255), (r * 0.85f).toInt(), (g * 0.85f).toInt(), (b * 0.9f).toInt())
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, glassBasePaint)

            // 2. Ghép ảnh 3D render sẵn từ res/drawable-nodpi/ (nếu có)
            val artName = resolveArtResourceName(effectType, lunarDay)
            val artBmp = if (artName != null) load3DArtBitmap(context, artName) else null
            if (artBmp != null) {
                val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    this.alpha = 235
                }
                // Vẽ ảnh 3D giữ nguyên tỷ lệ gốc (Center-Crop) để không bao giờ bị kéo giãn méo hình
                val srcRatio = artBmp.width.toFloat() / artBmp.height.toFloat()
                val dstRatio = w.toFloat() / h.toFloat()
                val dstRect = if (srcRatio > dstRatio) {
                    val scaledW = h * srcRatio
                    val left = (w - scaledW) / 2f
                    RectF(left, 0f, left + scaledW, h.toFloat())
                } else {
                    val scaledH = w / srcRatio
                    val top = (h - scaledH) / 2f
                    RectF(0f, top, w.toFloat(), top + scaledH)
                }
                canvas.drawBitmap(artBmp, null, dstRect, artPaint)
            }

            // Nếu lễ Tết trùng trời mưa thật, phủ thêm lớp ảnh mưa 3D bán trong suốt
            if (isRealRain && (effectType == "TET" || effectType == "NATIONAL" || effectType == "MID_AUTUMN")) {
                load3DArtBitmap(context, "art_rain")?.let { rainBmp ->
                    val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        this.alpha = 145
                    }
                    canvas.drawBitmap(rainBmp, null, rect, rainPaint)
                }
            }

            // 3. Lớp phản quang bề mặt kính lỏng (Specular Liquid Sheen)
            val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, w * 0.85f, h * 0.85f,
                    intArrayOf(
                        Color.argb(58, 255, 255, 255),
                        Color.argb(12, 255, 255, 255),
                        Color.argb(0, 255, 255, 255),
                        Color.argb(32, 255, 255, 255)
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(rect, sheenPaint)

            // 4. Viền sáng trong (Inner Rim Highlight) tạo độ dày mép kính 3D
            val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    intArrayOf(Color.argb(95, 255, 255, 255), Color.TRANSPARENT, Color.argb(45, 255, 255, 255)),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(4f, 4f, w - 4f, h - 4f), radius - 2f, radius - 2f, innerRimPaint)

            canvas.restore()

            // 5. Viền ngoài sắc nét phản chiếu ánh sáng góc chéo
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.6f
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(
                        Color.argb(235, 255, 255, 255),
                        Color.argb(55, 255, 255, 255),
                        Color.argb(165, 255, 255, 255)
                    ),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            return bmp
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
