package com.custom.liquidwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            Toast.makeText(this, "Đã cấp quyền vị trí, đang tải thời tiết...", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Đã nạp phông chữ tùy chỉnh!", Toast.LENGTH_SHORT).show()
                refreshWidgets()
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi đọc file phông chữ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        // Xin quyền vị trí nếu chưa có
        val hasLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasLoc) {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        val sbClockSize = findViewById<SeekBar>(R.id.sb_clock_size)
        val tvClockSizeVal = findViewById<TextView>(R.id.tv_clock_size_val)
        val sbTextScale = findViewById<SeekBar>(R.id.sb_text_scale)
        val tvTextScaleVal = findViewById<TextView>(R.id.tv_text_scale_val)
        val sbBgAlpha = findViewById<SeekBar>(R.id.sb_bg_alpha)
        val tvBgAlphaVal = findViewById<TextView>(R.id.tv_bg_alpha_val)

        val spLayoutMode = findViewById<Spinner>(R.id.sp_layout_mode)
        val spFontType = findViewById<Spinner>(R.id.sp_font_type)
        val spBgColor = findViewById<Spinner>(R.id.sp_bg_color)
        val spAccentColor = findViewById<Spinner>(R.id.sp_accent_color)
        val swEnableEffects = findViewById<Switch>(R.id.sw_enable_effects)
        val spTestEffect = findViewById<Spinner>(R.id.sp_test_effect)

        val btnPickFont = findViewById<Button>(R.id.btn_pick_font)
        val btnSyncWeather = findViewById<Button>(R.id.btn_sync_weather)
        val btnPinWidget = findViewById<Button>(R.id.btn_pin_widget)
        val btnApply = findViewById<Button>(R.id.btn_apply)

        // 1. SeekBars
        val initClock = prefs.getInt("clock_size", 36)
        sbClockSize.progress = (initClock - 24).coerceIn(0, 36)
        tvClockSizeVal.text = "${initClock}sp"
        sbClockSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvClockSizeVal.text = "${p + 24}sp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val initScale = prefs.getInt("text_scale", 95)
        sbTextScale.progress = (initScale - 70).coerceIn(0, 60)
        tvTextScaleVal.text = "$initScale%"
        sbTextScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvTextScaleVal.text = "${p + 70}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val initAlpha = prefs.getInt("bg_alpha", 48)
        sbBgAlpha.progress = initAlpha.coerceIn(0, 100)
        tvBgAlphaVal.text = "$initAlpha%"
        sbBgAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                tvBgAlphaVal.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 2. Spinners
        val layoutLabels = listOf("Giờ bên Trái (Mặc định)", "Giờ Căn Giữa", "Giờ bên Phải")
        spLayoutMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, layoutLabels)
        spLayoutMode.setSelection(prefs.getInt("layout_mode", 0).coerceIn(0, 2))

        val fontLabels = listOf("Mặc định (Sans Bold)", "Chữ có chân (Serif)", "Chữ kỹ thuật (Mono)", "Nét mảnh (Light)", "Nghiêng nghệ thuật (Italic)", "Phông tùy chỉnh (.ttf)")
        val fontValues = listOf("DEFAULT", "SERIF", "MONO", "LIGHT", "ITALIC", "CUSTOM")
        spFontType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fontLabels)
        val curFontIdx = fontValues.indexOf(prefs.getString("font_type", "DEFAULT")).coerceAtLeast(0)
        spFontType.setSelection(curFontIdx)

        val bgLabels = listOf("Xanh Đêm Sâu (#0F172A)", "Đen Kính Mờ (#090D16)", "Xanh Đại Dương (#0C4A6E)", "Tím Hoàng Hôn (#311042)", "Rêu Ngọc Lục (#064E3B)")
        val bgValues = listOf("#0F172A", "#090D16", "#0C4A6E", "#311042", "#064E3B")
        spBgColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bgLabels)
        spBgColor.setSelection(bgValues.indexOf(prefs.getString("bg_color", "#0F172A")).coerceAtLeast(0))

        val accentLabels = listOf("Vàng Nắng Ấm (#FFD54F)", "Xanh Băng Giá (#7DD3FC)", "Hồng Hoa Đào (#FDA4AF)", "Xanh Bạc Hà (#6EE7B7)", "Trắng Tinh Khôi (#FFFFFF)")
        val accentValues = listOf("#FFD54F", "#7DD3FC", "#FDA4AF", "#6EE7B7", "#FFFFFF")
        spAccentColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, accentLabels)
        spAccentColor.setSelection(accentValues.indexOf(prefs.getString("accent_color", "#FFD54F")).coerceAtLeast(0))

        swEnableEffects.isChecked = prefs.getBoolean("enable_effects", true)

        val effectLabels = listOf(
            "Tự động theo Thời tiết & Lễ Tết (AUTO)",
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
        val effectValues = listOf("AUTO", "RAIN", "THUNDER", "SUN_CLOUD", "MOON", "CLOUD", "TET", "NATIONAL", "MID_AUTUMN", "XMAS")
        spTestEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, effectLabels)
        spTestEffect.setSelection(effectValues.indexOf(prefs.getString("test_effect", "AUTO")).coerceAtLeast(0))

        // 3. Buttons
        btnPickFont.setOnClickListener {
            pickFontLauncher.launch("*/*")
        }

        btnSyncWeather.setOnClickListener {
            WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<WeatherWorker>().build())
            Toast.makeText(this, "Đang đồng bộ thời tiết thực tế...", Toast.LENGTH_SHORT).show()
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
            prefs.edit()
                .putInt("clock_size", sbClockSize.progress + 24)
                .putInt("text_scale", sbTextScale.progress + 70)
                .putInt("bg_alpha", sbBgAlpha.progress)
                .putInt("layout_mode", spLayoutMode.selectedItemPosition)
                .putString("font_type", fontValues[spFontType.selectedItemPosition])
                .putString("bg_color", bgValues[spBgColor.selectedItemPosition])
                .putString("accent_color", accentValues[spAccentColor.selectedItemPosition])
                .putBoolean("enable_effects", swEnableEffects.isChecked)
                .putString("test_effect", effectValues[spTestEffect.selectedItemPosition])
                .apply()

            refreshWidgets()
            Toast.makeText(this, "Đã lưu & cập nhật Lịch Âm Widget!", Toast.LENGTH_SHORT).show()
        }
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
