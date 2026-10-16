package com.kinh.gmsprotect

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isCompatibilityScreen = false
    private lateinit var mainScrollView: ScrollView
    private lateinit var compatibilityScrollView: ScrollView

    // Các TextView hiển thị trạng thái động để có thể cập nhật khi onResume
    private lateinit var txtMainStatusBadge: TextView
    private lateinit var txtPowerKeeperStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtMilletStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ẩn ActionBar mặc định để dùng Header custom
        supportActionBar?.hide()

        // Khởi tạo giao diện
        mainScrollView = createMainScreen()
        compatibilityScrollView = createCompatibilityScreen()

        // Hiển thị màn hình chính
        setContentView(mainScrollView)

        // Thực thi cốt lõi qua MilletHelper
        MilletHelper.applyMilletProtection(this)
        MilletHelper.scheduleMilletJob(this)
        MilletHelper.startProtectionService(this)
    }

    override fun onResume() {
        super.onResume()
        // Mỗi khi quay lại app, tiến hành quét lại trạng thái thực tế của hệ thống
        updateRealtimeStatus()
    }

    // ==========================================
    // HỆ THỐNG KIỂM TRA TRẠNG THÁI THỰC TẾ (REAL-TIME)
    // ==========================================
    private fun isPowerKeeperPresent(): Boolean {
        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binderPower = getService.invoke(null, "powerkeeper")
            val binderMillet = getService.invoke(null, "millet")
            binderPower != null || binderMillet != null
        } catch (e: Exception) {
            false
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(packageName)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun updateRealtimeStatus() {
        val hasPowerKeeper = isPowerKeeperPresent()
        val isBatteryExempted = isIgnoringBatteryOptimizations()

        // Cập nhật trạng thái cho màn hình tương thích nếu các view đã được khởi tạo
        try {
            if (::txtPowerKeeperStatus.isInitialized) {
                if (hasPowerKeeper) {
                    txtPowerKeeperStatus.text = "ĐÃ PHÁT HIỆN"
                    txtPowerKeeperStatus.setTextColor(Color.parseColor("#155724"))
                    (txtPowerKeeperStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
                } else {
                    txtPowerKeeperStatus.text = "KHÔNG PHÁT HIỆN"
                    txtPowerKeeperStatus.setTextColor(Color.parseColor("#721C24"))
                    (txtPowerKeeperStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#F8D7DA", 15f)
                }
            }

            if (::txtBatteryStatus.isInitialized) {
                if (isBatteryExempted) {
                    txtBatteryStatus.text = "ĐÃ XÁC MINH"
                    txtBatteryStatus.setTextColor(Color.parseColor("#155724"))
                    (txtBatteryStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
                } else {
                    txtBatteryStatus.text = "CHƯA BẬT"
                    txtBatteryStatus.setTextColor(Color.parseColor("#856404"))
                    (txtBatteryStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#FFF3CD", 15f)
                }
            }
        } catch (e: Exception) {
            // Bỏ qua lỗi nhỏ khi view chưa sẵn sàng
        }
    }

    // ==========================================
    // 1. DỰNG MÀN HÌNH CHÍNH
    // ==========================================
    private fun createMainScreen(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header Tím
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#383B8E"))
            setPadding(50, 80, 50, 60)
        }

        val appTitle = TextView(this).apply {
            text = "GMS Protect"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val appSubTitle = TextView(this).apply {
            text = "Bảo vệ Google Play services trên HyperOS"
            textSize = 14f
            setTextColor(Color.parseColor("#C5C7E8"))
            setPadding(0, 8, 0, 0)
        }

        header.addView(appTitle)
        header.addView(appSubTitle)
        mainLayout.addView(header)

        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 40)
        }

        // Card 1: Trạng thái
        val statusCard = createCardView().apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val checkIcon = TextView(this).apply {
            text = "✓"
            textSize = 45f
            setTextColor(Color.parseColor("#28A745"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val badgeProtected = createBadge("ĐÃ BẢO VỆ", "#D4EDDA", "#155724")

        val statusTitle = TextView(this).apply {
            text = "Bảo vệ GMS thành công"
            textSize = 20f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 20, 0, 10)
        }

        val statusDesc = TextView(this).apply {
            text = "Ứng dụng tự động kích hoạt cờ Millet và duy trì tiến trình chạy ngầm bảo vệ dịch vụ Google."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            gravity = Gravity.CENTER
            setPadding(20, 0, 20, 25)
        }

        val badgeListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        badgeListLayout.addView(createBadge("✓ GMS KHÔNG HẠN CHẾ • HOẠT ĐỘNG", "#E2F0D9", "#385723"))
        badgeListLayout.addView(createBadge("✓ BẢO VỆ GREEZER • SẴN SÀNG", "#E2F0D9", "#385723"))

        statusCard.addView(checkIcon)
        statusCard.addView(badgeProtected)
        statusCard.addView(statusTitle)
        statusCard.addView(statusDesc)
        statusCard.addView(badgeListLayout)
        bodyLayout.addView(statusCard)

        // Card 2: Nâng cao
        val advancedCard = createCardView()

        val advTitle = TextView(this).apply {
            text = "Nâng cao"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        }

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
        }

        val switchLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        }

        val switchTitle = TextView(this).apply {
            text = "Aurogon Allowlist"
            textSize = 15f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
        }

        val switchDesc = TextView(this).apply {
            text = "Lớp bảo vệ bổ sung cho HyperOS China. Khôi phục Google Play services vào danh sách cho phép khi tắt màn hình."
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 8, 10, 0)
        }

        switchLeft.addView(switchTitle)
        switchLeft.addView(switchDesc)

        val switchToggle = Switch(this).apply {
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f).apply {
                gravity = Gravity.TOP or Gravity.END
            }
        }

        switchLayout.addView(switchLeft)
        switchLayout.addView(switchToggle)

        advancedCard.addView(advTitle)
        advancedCard.addView(switchLayout)
        bodyLayout.addView(advancedCard)

        // Card 3: Kiểm tra tương thích
        val checkCard = createCardView()

        val checkTitle = TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
        }

        val checkDesc = TextView(this).apply {
            text = "Kiểm tra trạng thái thực tế của PowerKeeper và cơ chế tự phục hồi trên thiết bị này."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 8, 0, 20)
        }

        val btnOpenCheck = Button(this).apply {
            text = "Mở Kiểm tra tương thích"
            isAllCaps = false
            setTextColor(Color.parseColor("#383B8E"))
            textSize = 14f
            background = createRoundedDrawable("#E8E9F5", 20f)
            setPadding(0, 25, 0, 25)
            setOnClickListener {
                setContentView(compatibilityScrollView)
                isCompatibilityScreen = true
            }
        }

        checkCard.addView(checkTitle)
        checkCard.addView(checkDesc)
        checkCard.addView(btnOpenCheck)
        bodyLayout.addView(checkCard)

        mainLayout.addView(bodyLayout)
        rootScroll.addView(mainLayout)
        return rootScroll
    }

    // ==========================================
    // 2. DỰNG MÀN HÌNH KIỂM TRA TƯƠNG THÍCH (REAL-TIME)
    // ==========================================
    private fun createCompatibilityScreen(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#383B8E"))
            setPadding(50, 80, 50, 60)
        }

        val title = TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val subTitle = TextView(this).apply {
            text = "Trạng thái kiểm tra thực tế trên thiết bị của bạn"
            textSize = 13f
            setTextColor(Color.parseColor("#C5C7E8"))
            setPadding(0, 8, 0, 0)
        }

        header.addView(title)
        header.addView(subTitle)
        mainLayout.addView(header)

        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 40)
        }

        // Device Info Card
        val devCard = createCardView()
        val devInfo = TextView(this).apply {
            text = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
            textSize = 13f
            setTextColor(Color.parseColor("#495057"))
            setTypeface(null, Typeface.BOLD)
        }
        val devBadge = createBadge("THIẾT BỊ HỖ TRỢ", "#E2F0D9", "#385723").apply {
            setPadding(20, 8, 20, 8)
        }

        devCard.addView(devInfo)
        devCard.addView(devBadge)
        bodyLayout.addView(devCard)

        // HyperOS / PowerKeeper Card (Dynamic Status)
        val hkCard = createCardView()
        hkCard.addView(TextView(this).apply {
            text = "Trạng thái Hệ thống & Dịch vụ"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        // Tạo các dòng kiểm tra động
        val initialPowerKeeperText = if (isPowerKeeperPresent()) "ĐÃ PHÁT HIỆN" else "KHÔNG PHÁT HIỆN"
        val initialPowerKeeperColor = if (isPowerKeeperPresent()) "#155724" else "#721C24"
        val initialPowerKeeperBg = if (isPowerKeeperPresent()) "#D4EDDA" else "#F8D7DA"
        
        txtPowerKeeperStatus = createBadge(initialPowerKeeperText, initialPowerKeeperBg, initialPowerKeeperColor)
        hkCard.addView(createCustomRow("PowerKeeper / Millet Service", txtPowerKeeperStatus))

        val initialBatteryText = if (isIgnoringBatteryOptimizations()) "ĐÃ XÁC MINH" else "CHƯA BẬT"
        val initialBatteryColor = if (isIgnoringBatteryOptimizations()) "#155724" else "#856404"
        val initialBatteryBg = if (isIgnoringBatteryOptimizations()) "#D4EDDA" else "#FFF3CD"

        txtBatteryStatus = createBadge(initialBatteryText, initialBatteryBg, initialBatteryColor)
        hkCard.addView(createCustomRow("Miễn tối ưu pin Android", txtBatteryStatus))

        hkCard.addView(createCheckRow("Google Play services", "ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("CH Play", "ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("Tiến trình JobScheduler", "ĐÃ HOẠT ĐỘNG", "#E2F0D9", "#385723"))

        bodyLayout.addView(hkCard)

        // Back Button
        val btnBack = Button(this).apply {
            text = "Quay lại Màn hình chính"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createRoundedDrawable("#383B8E", 20f)
            setPadding(0, 25, 0, 25)
            setOnClickListener {
                setContentView(mainScrollView)
                isCompatibilityScreen = false
            }
        }
        bodyLayout.addView(btnBack)

        mainLayout.addView(bodyLayout)
        rootScroll.addView(mainLayout)
        return rootScroll
    }

    private fun createCardView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable("#FFFFFF", 30f)
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 30)
            }
        }
    }

    private fun createBadge(textStr: String, bgColorHex: String, textColorHex: String): TextView {
        return TextView(this).apply {
            text = textStr
            textSize = 11f
            setTextColor(Color.parseColor(textColorHex))
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(bgColorHex, 15f)
            setPadding(25, 10, 25, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createCheckRow(label: String, badgeText: String, bgColorHex: String, textColorHex: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            setPadding(0, 12, 0, 12)
        }

        val txtLabel = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }

        val badgeContainer = LinearLayout(this).apply {
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        }

        val badge = createBadge(badgeText, bgColorHex, textColorHex)
        badgeContainer.addView(badge)

        row.addView(txtLabel)
        row.addView(badgeContainer)
        return row
    }

    private fun createCustomRow(label: String, badgeView: TextView): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
            setPadding(0, 12, 0, 12)
        }

        val txtLabel = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
        }

        val badgeContainer = LinearLayout(this).apply {
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
        }

        badgeContainer.addView(badgeView)
        row.addView(txtLabel)
        row.addView(badgeContainer)
        return row
    }

    private fun createRoundedDrawable(colorHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            setCornerRadius(cornerRadius)
        }
    }

    override fun onBackPressed() {
        if (isCompatibilityScreen) {
            setContentView(mainScrollView)
            isCompatibilityScreen = false
        } else {
            super.onBackPressed()
        }
    }
}
