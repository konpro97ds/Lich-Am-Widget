package com.custom.liquidwidget

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object LunarCalendar {

    data class HolidayInfo(
        val name: String,
        val themeType: String // "NONE", "TET", "NATIONAL", "MID_AUTUMN", "XMAS"
    )

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    /**
     * Chuyển đổi Dương lịch sang Âm lịch Việt Nam (múi giờ UTC+7)
     * Trả về Triple(ngày Âm, tháng Âm, chuỗi mô tả đầy đủ kèm Can Chi)
     */
    fun getLunarInfo(dd: Int, mm: Int, yy: Int): Triple<Int, Int, String> {
        val lunar = convertSolar2Lunar(dd, mm, yy, 7.0)
        val lunarDay = lunar[0]
        val lunarMonth = lunar[1]
        val lunarYear = lunar[2]
        val isLeap = lunar[3] == 1

        val canChiYear = "${CAN[(lunarYear + 6) % 10]} ${CHI[(lunarYear + 8) % 12]}"
        val leapStr = if (isLeap) " (Nhuận)" else ""
        val dayPrefix = if (lunarDay <= 10) "Mùng $lunarDay" else "$lunarDay"
        val fullText = "$dayPrefix/$lunarMonth$leapStr · Năm $canChiYear"

        return Triple(lunarDay, lunarMonth, fullText)
    }

    /**
     * Kiểm tra ngày lễ Việt Nam & Quốc tế trong ngày hiện tại
     */
    fun getVietnameseHoliday(dd: Int, mm: Int, yy: Int): HolidayInfo {
        val lunar = convertSolar2Lunar(dd, mm, yy, 7.0)
        val lDay = lunar[0]
        val lMonth = lunar[1]
        val isLeap = lunar[3] == 1

        // Kiểm tra ngày 30 (hoặc 29 thiếu) tháng Chạp (Tất Niên)
        val tomorrow = Calendar.getInstance().apply {
            set(yy, mm - 1, dd)
            add(Calendar.DAY_OF_MONTH, 1)
        }
        val tmrLunar = convertSolar2Lunar(
            tomorrow.get(Calendar.DAY_OF_MONTH),
            tomorrow.get(Calendar.MONTH) + 1,
            tomorrow.get(Calendar.YEAR),
            7.0
        )
        if (!isLeap && lMonth == 12 && tmrLunar[0] == 1 && tmrLunar[1] == 1) {
            return HolidayInfo("🧧 Giao Thừa · Tất Niên", "TET")
        }

        // Lễ theo Âm lịch
        if (!isLeap) {
            when {
                lMonth == 1 && lDay in 1..3 -> return HolidayInfo("🧧 Tết Nguyên Đán (Mùng $lDay)", "TET")
                lMonth == 1 && lDay == 15 -> return HolidayInfo("🏮 Rằm Tháng Giêng", "NONE")
                lMonth == 3 && lDay == 10 -> return HolidayInfo("🏛️ Giỗ Tổ Hùng Vương", "NATIONAL")
                lMonth == 4 && lDay == 15 -> return HolidayInfo("🪷 Lễ Phật Đản", "NONE")
                lMonth == 5 && lDay == 5 -> return HolidayInfo("🌿 Tết Đoan Ngọ", "NONE")
                lMonth == 7 && lDay == 15 -> return HolidayInfo("🌹 Lễ Vu Lan", "NONE")
                lMonth == 8 && lDay == 15 -> return HolidayInfo("🌕 Tết Trung Thu", "MID_AUTUMN")
                lMonth == 12 && lDay == 23 -> return HolidayInfo("🐟 Ông Công Ông Táo", "TET")
            }
        }

        // Lễ theo Dương lịch
        return when {
            dd == 1 && mm == 1 -> HolidayInfo("🎉 Tết Dương Lịch", "TET")
            dd == 14 && mm == 2 -> HolidayInfo("💝 Lễ Tình Nhân", "NONE")
            dd == 8 && mm == 3 -> HolidayInfo("💐 Quốc Tế Phụ Nữ", "NONE")
            dd == 30 && mm == 4 -> HolidayInfo("🇻🇳 Giải Phóng Miền Nam", "NATIONAL")
            dd == 1 && mm == 5 -> HolidayInfo("🛠️ Quốc Tế Lao Động", "NATIONAL")
            dd == 1 && mm == 6 -> HolidayInfo("🎈 Quốc Tế Thiếu Nhi", "NONE")
            dd == 2 && mm == 9 -> HolidayInfo("🇻🇳 Quốc Khánh 2/9", "NATIONAL")
            dd == 20 && mm == 10 -> HolidayInfo("🌸 Phụ Nữ Việt Nam", "NONE")
            dd == 20 && mm == 11 -> HolidayInfo("📚 Nhà Giáo Việt Nam", "NONE")
            dd == 24 && mm == 12 -> HolidayInfo("🎄 Đêm Giáng Sinh", "XMAS")
            dd == 25 && mm == 12 -> HolidayInfo("🎅 Lễ Giáng Sinh", "XMAS")
            else -> HolidayInfo("", "NONE")
        }
    }

    /**
     * Đếm ngược ngày lễ lớn gần nhất trong vòng 15 ngày tới
     */
    fun getNextUpcomingHoliday(dd: Int, mm: Int, yy: Int): String {
        val cal = Calendar.getInstance().apply { set(yy, mm - 1, dd) }
        for (offset in 1..15) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val h = getVietnameseHoliday(
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR)
            )
            if (h.name.isNotEmpty()) {
                val cleanName = h.name.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim()
                return "Còn $offset ngày tới $cleanName"
            }
        }
        return ""
    }

    // --- Thuật toán thiên văn Âm lịch Việt Nam (Hồ Ngọc Đức) ---

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun getNewMoonDay(k: Int, timeZone: Double): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 = c1 - 0.4068 * sin(mpr * dr) + 0.0161 * sin(dr * 2 * mpr)
        c1 -= 0.0004 * sin(dr * 3 * mpr)
        c1 = c1 + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mpr))
        c1 = c1 - 0.0074 * sin(dr * (m - mpr)) + 0.0004 * sin(dr * (2 * f + m))
        c1 = c1 - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(dr * (2 * f - mpr)) + 0.0005 * sin(dr * (2 * mpr + m))
        val deltat = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        val jdNew = jd1 + c1 - deltat
        return floor(jdNew + 0.5 + timeZone / 24.0).toInt()
    }

    private fun getSunLongitude(jdn: Int, timeZone: Double): Int {
        val t = (jdn - 2451545.5 - timeZone / 24.0) / 36525.0
        val t2 = t * t
        val dr = PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return floor(l / PI * 6).toInt()
    }

    private fun getLunarMonth11(yy: Int, timeZone: Double): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = floor(off / 29.530588853).toInt()
        var nm = getNewMoonDay(k, timeZone)
        val sunLong = getSunLongitude(nm, timeZone)
        if (sunLong >= 9) {
            nm = getNewMoonDay(k - 1, timeZone)
        }
        return nm
    }

    private fun getLeapMonthOffset(a11: Int, timeZone: Double): Int {
        val k = floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        do {
            last = arc
            i++
            arc = getSunLongitude(getNewMoonDay(k + i, timeZone), timeZone)
        } while (arc != last && i < 14)
        return i - 1
    }

    private fun convertSolar2Lunar(dd: Int, mm: Int, yy: Int, timeZone: Double): IntArray {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = getNewMoonDay(k + 1, timeZone)
        if (monthStart > dayNumber) {
            monthStart = getNewMoonDay(k, timeZone)
        }
        var a11 = getLunarMonth11(yy, timeZone)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = getLunarMonth11(yy - 1, timeZone)
        } else {
            lunarYear = yy + 1
            b11 = getLunarMonth11(yy + 1, timeZone)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var lunarLeap = 0
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = getLeapMonthOffset(a11, timeZone)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) {
                    lunarLeap = 1
                }
            }
        }
        if (lunarMonth > 12) {
            lunarMonth -= 12
        }
        if (lunarMonth >= 11 && diff < 4) {
            lunarYear -= 1
        }
        return intArrayOf(lunarDay, lunarMonth, lunarYear, lunarLeap)
    }
}
