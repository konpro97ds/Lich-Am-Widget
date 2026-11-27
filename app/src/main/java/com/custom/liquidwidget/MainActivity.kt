package com.custom.liquidwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private data class CityItem(val name: String, val lat: Float, val lon: Float)

    private val cityList = listOf(
        CityItem("📍 Tự động định vị khu vực của tôi", 0f, 0f),
        CityItem("TP. Hồ Chí Minh", 10.8231f, 106.6297f),
        CityItem("Hà Nội", 21.0285f, 105.8542f),
        CityItem("Đà Nẵng", 16.0544f, 108.2022f),
        CityItem("Hải Phòng", 20.8449f, 106.6881f),
        CityItem("Cần Thơ", 10.0452f, 105.7469f),
        CityItem("Nha Trang (Khánh Hòa)", 12.2388f, 109.1967f),
        CityItem("Đà Lạt (Lâm Đồng)", 11.9404f, 108.4583f),
        CityItem("Vũng Tàu (Bà Rịa - VT)", 10.4114f, 107.1362f),
        CityItem("Huế (Thừa Thiên Huế)", 16.4637f, 107.5909f),
        CityItem("Quy Nhơn (Bình Định)", 13.7820f, 109.2197f),
        CityItem("Buôn Ma Thuột (Đắk Lắk)", 12.6667f, 108.0500f),
        CityItem("Biên Hòa (Đồng Nai)", 10.9453f, 106.8243f),
        CityItem("Thủ Dầu Một (Bình Dương)", 10.9804f, 106.6519f),
        CityItem("Vinh (Nghệ An)", 18.6796f, 105.6813f),
        CityItem("Thanh Hóa", 19.8067f, 105.7852f),
        CityItem("Hạ Long (Quảng Ninh)", 20.9599f, 107.0425f),
        CityItem("Phú Quốc (Kiên Giang)", 10.2899f, 103.9840f),
        CityItem("Cà Mau", 9.1769f, 105.1524f)
    )

    private val fontLabels = listOf(
        "Mặc định (Sans Bold)",
        "Chữ có chân sang trọng (Serif)",
        "Chữ kỹ thuật số (Monospace)",
        "Nét mảnh hiện đại (Sans Light)",
        "Nghiêng nghệ thuật (Serif Italic)",
        "Phông chữ riêng đã nạp (.ttf)"
    )
    private val fontValues = listOf("DEFAULT", "SERIF", "MONO", "LIGHT", "ITALIC", "CUSTOM")

    private val bgLabels = listOf("Xanh Đêm Sâu (#0F172A)", "Đen Kính Mờ (#090D16)", "Xanh Đại Dương (#0C4A6E)", "Tím Hoàng Hôn (#311042)", "Rêu Ngọc Lục (#064E3B)")
    private val bgValues = listOf("#0F172A", "#090D16", "#0C4A6E", "#311042", "#064E3B")

    private val accentLabels = listOf("Vàng Nắng Ấm (#FFD54F)", "Xanh Băng Giá (#7DD3FC)", "Hồng Hoa Đào (#FDA4AF)", "Xanh Bạc Hà (#6EE7B7)", "Trắng Tinh Khôi (#FFFFFF)")
    private val accentValues = listOf("#FFD54F", "#7DD3FC", "#FDA4AF", "#6EE7B7", "#FFFFFF")

    private val effectLabels = listOf(
        "Tự động theo Thời tiết & Lễ Tết",
        "Mưa rơi & Giọt đọng (RAIN)",
        "Giông bão & Sấm chớp (THUNDER)",
        "Nắng ấm & Mây trôi (SUN_CLOUD)",
        "Trăng sao ban đêm (MOON)",
        "Mây nhiều âm u (CLOUD)",
        "Tết Nguyên Đán & Pháo hoa (TET)",
        "Quốc Khánh Cờ Đỏ Sao Vàng (NATIONAL)",
        "Tết Trung Thu Trăng Rằm (MID_AUTUMN)",
        "Giáng Sinh Tuyết Rơi (XMAS)"
    )
    private val effectValues = listOf("AUTO", "RAIN", "THUNDER", "SUN_CLOUD", "MOON", "CLOUD", "TET", "NATIONAL", "MID_AUTUMN", "XMAS")

    private lateinit var ivPreviewBg: ImageView
    private lateinit var prevLeftCol: LinearLayout
    private lateinit var prevRightCol: LinearLayout
    private lateinit var tvPrevGreeting: TextView
    private lateinit var tvPrevClock: TextView
    private lateinit var tvPrevSolar: TextView
    private lateinit var tvPrevLunar: TextView
    private lateinit var tvPrevTemp: TextView
    private lateinit var tvPrevDesc: TextView
    private lateinit var tvPrevCity: TextView
    private lateinit var tvPrevRange: TextView
    private lateinit var tvPrevWeekBar: TextView

    private lateinit var sbClockSize: SeekBar
    private lateinit var tvClockSizeVal: TextView
    private lateinit var sbTextScale: SeekBar
    private lateinit var tvTextScaleVal: TextView
    private lateinit var sbBgAlpha: SeekBar
    private lateinit var tvBgAlphaVal: TextView

    private lateinit var spCitySelect: Spinner
    private lateinit var spLayoutMode: Spinner
    private lateinit var spFontType: Spinner
    private lateinit var spBgColor: Spinner
    private lateinit var spAccentColor: Spinner
    private lateinit var swEnableEffects: Switch
    private lateinit var spTestEffect: Spinner

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerWeatherSync()
            Toast.makeText(this, "Đã cấp quyền vị trí, đang lấy thời tiết khu vực của bạn...", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickFontLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outFile = File(filesDir, "custom_font.ttf")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("font_type", "CUSTOM")
                    .apply()
                spFontType.setSelection(fontValues.indexOf("CUSTOM"))
                updateLivePreview()
                refreshWidgets()
                Toast.makeText(this, "Đã nạp phông chữ riêng thành công!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc file phông chữ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        // Bind Preview views
        ivPreviewBg = findViewById(R.id.iv_preview_bg)
        prevLeftCol = findViewById(R.id.prev_left_col)
        prevRightCol = findViewById(R.id.prev_right_col)
        tvPrevGreeting = findViewById(R.id.tv_prev_greeting)
        tvPrevClock = findViewById(R.id.tv_prev_clock)
        tvPrevSolar = findViewById(R.id.tv_prev_solar)
        tvPrevLunar = findViewById(R.id.tv_prev_lunar)
        tvPrevTemp = findViewById(R.id.tv_prev_temp)
        tvPrevDesc = findViewById(R.id.tv_prev_desc)
        tvPrevCity = findViewById(R.id.tv_prev_city)
        tvPrevRange = findViewById(R.id.tv_prev_range)
        tvPrevWeekBar = findViewById(R.id.tv_prev_week_bar)

        // Bind Controls
        sbClockSize = findViewById(R.id.sb_clock_size)
        tvClockSizeVal = findViewById(R.id.tv_clock_size_val)
        sbTextScale = findViewById(R.id.sb_text_scale)
        tvTextScaleVal = findViewById(R.id.tv_text_scale_val)
        sbBgAlpha = findViewById(R.id.sb_bg_alpha)
        tvBgAlphaVal = findViewById(R.id.tv_bg_alpha_val)

        spCitySelect = findViewById(R.id.sp_city_select)
        spLayoutMode = findViewById(R.id.sp_layout_mode)
        spFontType = findViewById(R.id.sp_font_type)
        spBgColor = findViewById(R.id.sp_bg_color)
        spAccentColor = findViewById(R.id.sp_accent_color)
        swEnableEffects = findViewById(R.id.sw_enable_effects)
        spTestEffect = findViewById(R.id.sp_test_effect)

        val btnPickFont = findViewById<Button>(R.id.btn_pick_font)
        val btnSyncWeather = findViewById<Button>(R.id.btn_sync_weather)
        val btnPinWidget = findViewById<Button>(R.id.btn_pin_widget)
        val btnApply = findViewById<Button>(R.id.btn_apply)

        // 1. Setup City Spinner
        spCitySelect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cityList.map { it.name })
        val savedLocMode = prefs.getString("location_mode", "AUTO")
        val savedManualCity = prefs.getString("manual_city_name", "")
        val initCityIdx = if (savedLocMode == "MANUAL") {
            cityList.indexOfFirst { it.name == savedManualCity }.coerceAtLeast(1)
        } else 0
        spCitySelect.setSelection(initCityIdx)

        // 2. Setup SeekBars
        val initClock = prefs.getInt("clock_size", 36)
        sbClockSize.progress = (initClock - 24).coerceIn(0, 36)
        tvClockSizeVal.text = "${initClock}sp"

        val initScale = prefs.getInt("text_scale", 95)
        sbTextScale.progress = (initScale - 70).coerceIn(0, 60)
        tvTextScaleVal.text = "$initScale%"

        val initAlpha = prefs.getInt("bg_alpha", 48)
        sbBgAlpha.progress = initAlpha.coerceIn(0, 100)
        tvBgAlphaVal.text = "$initAlpha%"

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvClockSizeVal.text = "${sbClockSize.progress + 24}sp"
                tvTextScaleVal.text = "${sbTextScale.progress + 70}%"
                tvBgAlphaVal.text = "${sbBgAlpha.progress}%"
                updateLivePreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        sbClockSize.setOnSeekBarChangeListener(seekListener)
        sbTextScale.setOnSeekBarChangeListener(seekListener)
        sbBgAlpha.setOnSeekBarChangeListener(seekListener)

        // 3. Setup Other Spinners
        val layoutLabels = listOf("Giờ bên Trái (Mặc định)", "Giờ Căn Giữa", "Giờ bên Phải")
        spLayoutMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, layoutLabels)
        spLayoutMode.setSelection(prefs.getInt("layout_mode", 0).coerceIn(0, 2))

        spFontType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontLabels)
        spFontType.setSelection(fontValues.indexOf(prefs.getString("font_type", "DEFAULT")).coerceAtLeast(0))

        spBgColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bgLabels)
        spBgColor.setSelection(bgValues.indexOf(prefs.getString("bg_color", "#0F172A")).coerceAtLeast(0))

        spAccentColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, accentLabels)
        spAccentColor.setSelection(accentValues.indexOf(prefs.getString("accent_color", "#FFD54F")).coerceAtLeast(0))

        swEnableEffects.isChecked = prefs.getBoolean("enable_effects", true)

        spTestEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, effectLabels)
        spTestEffect.setSelection(effectValues.indexOf(prefs.getString("test_effect", "AUTO")).coerceAtLeast(0))

        val itemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateLivePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spCitySelect.onItemSelectedListener = itemSelectedListener
        spLayoutMode.onItemSelectedListener = itemSelectedListener
        spFontType.onItemSelectedListener = itemSelectedListener
        spBgColor.onItemSelectedListener = itemSelectedListener
        spAccentColor.onItemSelectedListener = itemSelectedListener
        spTestEffect.onItemSelectedListener = itemSelectedListener
        swEnableEffects.setOnCheckedChangeListener { _, _ -> updateLivePreview() }

        // 4. Buttons
        btnPickFont.setOnClickListener {
            pickFontLauncher.launch("*/*")
        }

        btnSyncWeather.setOnClickListener {
            val selectedCity = cityList[spCitySelect.selectedItemPosition]
            if (spCitySelect.selectedItemPosition == 0) {
                prefs.edit().putString("location_mode", "AUTO").apply()
                val hasLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!hasLoc) {
                    locationPermLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                } else {
                    triggerWeatherSync()
                    Toast.makeText(this, "Đang lấy định vị khu vực của bạn...", Toast.LENGTH_SHORT).show()
                }
            } else {
                prefs.edit()
                    .putString("location_mode", "MANUAL")
                    .putString("manual_city_name", selectedCity.name)
                    .putFloat("manual_lat", selectedCity.lat)
                    .putFloat("manual_lon", selectedCity.lon)
                    .apply()
                triggerWeatherSync()
                Toast.makeText(this, "Đang tải thời tiết ${selectedCity.name}...", Toast.LENGTH_SHORT).show()
            }
            updateLivePreview()
        }

        btnPinWidget.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = getSystemService(AppWidgetManager::class.java)
                val myProvider = ComponentName(this, LiquidWidgetProvider::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                } else {
                    Toast.makeText(this, "Launcher không hỗ trợ ghim tự động, hãy thêm từ màn hình chính.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnApply.setOnClickListener {
            val selectedCityIdx = spCitySelect.selectedItemPosition
            val selectedCity = cityList[selectedCityIdx]
            val editor = prefs.edit()
                .putInt("clock_size", sbClockSize.progress + 24)
                .putInt("text_scale", sbTextScale.progress + 70)
                .putInt("bg_alpha", sbBgAlpha.progress)
                .putInt("layout_mode", spLayoutMode.selectedItemPosition)
                .putString("font_type", fontValues[spFontType.selectedItemPosition])
                .putString("bg_color", bgValues[spBgColor.selectedItemPosition])
                .putString("accent_color", accentValues[spAccentColor.selectedItemPosition])
                .putBoolean("enable_effects", swEnableEffects.isChecked)
                .putString("test_effect", effectValues[spTestEffect.selectedItemPosition])

            if (selectedCityIdx == 0) {
                editor.putString("location_mode", "AUTO")
            } else {
                editor.putString("location_mode", "MANUAL")
                    .putString("manual_city_name", selectedCity.name)
                    .putFloat("manual_lat", selectedCity.lat)
                    .putFloat("manual_lon", selectedCity.lon)
                    .putString("last_city", "🏙️ ${selectedCity.name}")
            }
            editor.apply()

            triggerWeatherSync()
            refreshWidgets()
            Toast.makeText(this, "Đã lưu & cập nhật Lịch Âm Widget!", Toast.LENGTH_SHORT).show()
        }

        updateLivePreview()
    }

    private fun updateLivePreview() {
        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val clockSize = (sbClockSize.progress + 24).toFloat()
        val textScale = (sbTextScale.progress + 70) / 100f
        val bgAlpha = sbBgAlpha.progress
        val bgColorHex = bgValues[spBgColor.selectedItemPosition]
        val accentColor = Color.parseColor(accentValues[spAccentColor.selectedItemPosition])
        val fontType = fontValues[spFontType.selectedItemPosition]
        val tf = LiquidWidgetProvider.loadCustomTypeface(this, fontType)
        val layoutMode = spLayoutMode.selectedItemPosition

        val cal = Calendar.getInstance()
        val dd = cal.get(Calendar.DAY_OF_MONTH)
        val mm = cal.get(Calendar.MONTH) + 1
        val yy = cal.get(Calendar.YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val isNight = (hour >= 18 || hour < 6)

        val lunarToday = LunarCalendar.getLunarInfo(dd, mm, yy)
        val holiday = LunarCalendar.getVietnameseHoliday(dd, mm, yy)
        val descStr = prefs.getString("last_desc", "Ít mây") ?: "Ít mây"
        val testEffectMode = effectValues[spTestEffect.selectedItemPosition]

        val activeEffect = if (!swEnableEffects.isChecked) "NONE"
        else if (testEffectMode != "AUTO") testEffectMode
        else if (holiday.themeType != "NONE") holiday.themeType
        else LiquidWidgetProvider.detectWeatherEffect(descStr, isNight)

        ivPreviewBg.setImageBitmap(
            LiquidWidgetProvider.drawLiquidGlassBitmap(bgColorHex, bgAlpha, 56f, activeEffect, isNight, lunarToday.first)
        )

        val dayOfWeek = arrayOf("", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy")[cal.get(Calendar.DAY_OF_WEEK)]
        tvPrevGreeting.text = when (hour) {
            in 5..10 -> "Chào buổi sáng ⚙"
            in 11..13 -> "Chào buổi trưa ⚙"
            in 14..17 -> "Chào buổi chiều ⚙"
            else -> "Chào buổi tối ⚙"
        }
        tvPrevClock.text = String.format("%02d:%02d", hour, min)
        tvPrevSolar.text = "$dayOfWeek, $dd Thg $mm, $yy"
        tvPrevLunar.text = if (holiday.name.isNotEmpty()) "${holiday.name} · ${lunarToday.third}" else lunarToday.third
        tvPrevTemp.text = prefs.getString("last_temp", "28°")
        tvPrevDesc.text = descStr
        tvPrevRange.text = prefs.getString("last_range", "↑32° ↓24°")

        val cityIdx = spCitySelect.selectedItemPosition
        tvPrevCity.text = if (cityIdx == 0) {
            prefs.getString("last_city", "📍 Khu vực của bạn")
        } else {
            "🏙️ ${cityList[cityIdx].name}"
        }

        // Cập nhật Font & Kích thước trên Preview
        listOf(tvPrevGreeting, tvPrevClock, tvPrevSolar, tvPrevLunar, tvPrevTemp, tvPrevDesc, tvPrevCity, tvPrevRange, tvPrevWeekBar).forEach {
            it.typeface = tf
        }
        tvPrevClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSize)
        tvPrevGreeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * textScale)
        tvPrevSolar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * textScale)
        tvPrevLunar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f * textScale)
        tvPrevTemp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f * textScale)
        tvPrevDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * textScale)
        tvPrevCity.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
        tvPrevRange.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * textScale)
        tvPrevWeekBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * textScale)

        tvPrevLunar.setTextColor(accentColor)
        tvPrevWeekBar.setTextColor(accentColor)

        // Cập nhật bố cục Trái / Giữa / Phải trong Preview
        when (layoutMode) {
            1 -> {
                prevRightCol.visibility = View.GONE
                prevLeftCol.gravity = Gravity.CENTER_HORIZONTAL
            }
            2 -> {
                prevRightCol.visibility = View.VISIBLE
                prevLeftCol.gravity = Gravity.END
                prevRightCol.gravity = Gravity.START
            }
            else -> {
                prevRightCol.visibility = View.VISIBLE
                prevLeftCol.gravity = Gravity.START
                prevRightCol.gravity = Gravity.END
            }
        }
    }

    private fun triggerWeatherSync() {
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
    }

    private fun refreshWidgets() {
        val intent = Intent(this, LiquidWidgetProvider::class.java).apply {
            action = "com.custom.liquidwidget.PLAY_EFFECT"
        }
        sendBroadcast(intent)

        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, LiquidWidgetProvider::class.java))
        for (id in ids) {
            LiquidWidgetProvider.updateAppWidget(this, manager, id)
        }
    }
}
