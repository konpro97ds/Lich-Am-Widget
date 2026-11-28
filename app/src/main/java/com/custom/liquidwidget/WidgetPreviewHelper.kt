package com.custom.liquidwidget

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import java.util.Calendar

data class WidgetPreviewConfig(
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

object WidgetPreviewHelper {

    fun renderPreview(context: Context, root: View, cfg: WidgetPreviewConfig) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val mainColor = Color.parseColor(cfg.textColorHex)
        val accentColor = Color.parseColor(cfg.accentColorHex)
        val typeface = LiquidWidgetProvider.loadCustomTypeface(context, cfg.fontType)

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
        else LiquidWidgetProvider.detectWeatherEffect(descStr, isNight)

        val aspectRatio = if (root.width > 0 && root.height > 0) {
            (root.width.toFloat() / root.height.toFloat()).coerceIn(1.6f, 2.8f)
        } else 2.15f

        root.findViewById<ImageView>(R.id.iv_glass_bg)?.setImageBitmap(
            LiquidWidgetProvider.drawLiquidGlassBitmap(cfg.bgColorHex, cfg.bgAlpha, 52f, activeEffect, isNight, lunarToday.first, aspectRatio)
        )
        root.findViewById<ProgressBar>(R.id.pb_glass_shimmer)?.visibility = if (cfg.enableEffects) View.VISIBLE else View.GONE

        val showRain = cfg.enableEffects && (activeEffect == "RAIN" || activeEffect == "THUNDER")
        val showSun = cfg.enableEffects && (activeEffect == "SUN" || activeEffect == "SUN_CLOUD")
        val showMoon = cfg.enableEffects && (activeEffect == "MOON" || activeEffect == "MOON_CLOUD" || activeEffect == "MID_AUTUMN")
        val showCloud = cfg.enableEffects && (activeEffect == "CLOUD" || activeEffect == "SUN_CLOUD" || activeEffect == "MOON_CLOUD" || activeEffect == "RAIN" || activeEffect == "THUNDER")
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
        intArrayOf(R.id.tv_colon, R.id.tv_colon_c, R.id.tv_colon_r).forEach { id ->
            root.findViewById<TextView>(id)?.apply {
                setTextColor(mainColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.clockSize * 0.92f)
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

        root.findViewById<ImageView>(R.id.iv_solar_custom)?.setImageBitmap(LiquidWidgetProvider.drawTextBitmap(context, solarStr, 12.5f * cfg.textScale, mainColor, typeface))
        root.findViewById<ImageView>(R.id.iv_lunar_custom)?.setImageBitmap(LiquidWidgetProvider.drawTextBitmap(context, displayLunarOrHoliday, 12f * cfg.textScale, accentColor, typeface))
        root.findViewById<ImageView>(R.id.iv_temp_custom)?.setImageBitmap(LiquidWidgetProvider.drawTextBitmap(context, tempStr, 31f * cfg.textScale, mainColor, typeface))

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
}
