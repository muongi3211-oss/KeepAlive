package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isCompatibilityScreen = false
    private lateinit var mainScrollView: ScrollView
    private lateinit var compatibilityScrollView: ScrollView
    private lateinit var sharedPreferences: SharedPreferences

    // Các TextView hiển thị trạng thái động cho cả 2 màn hình
    private lateinit var txtMainStatusTitle: TextView
    private lateinit var txtMainGmsBadge: TextView
    private lateinit var txtMainGreezerBadge: TextView

    private lateinit var txtPowerKeeperStatus: TextView
    private lateinit var txtBatteryStatus: TextView
    private lateinit var txtWriteSettingsStatus: TextView
    private lateinit var txtSystemPropStatus: TextView
    private lateinit var txtGmsServiceStatus: TextView
    private lateinit var txtPlayStoreStatus: TextView
    private lateinit var txtJobSchedulerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Khởi tạo SharedPreferences để lưu trạng thái bật/tắt nâng cao
            sharedPreferences = getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)

            // Ẩn ActionBar mặc định để dùng Header custom
            supportActionBar?.hide()

            // Khởi tạo giao diện
            mainScrollView = createMainScreen()
            compatibilityScrollView = createCompatibilityScreen()

            // Hiển thị màn hình chính
            setContentView(mainScrollView)

            // Thực thi cốt lõi qua MilletHelper và tự bảo vệ chính app chống nightZone
            MilletHelper.applyMilletProtection(this)
            MilletHelper.scheduleMilletJob(this)
            MilletHelper.startProtectionService(this)
        } catch (e: Throwable) {
            // Bắt trọn ngoại lệ khởi động để tuyệt đối không bị văng (Force Close) trên Android 14+
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Mỗi khi quay lại app, tiến hành quét lại toàn bộ trạng thái thực tế của hệ thống
            updateRealtimeStatus()
        } catch (e: Throwable) {}
    }

    // ==========================================
    // HỆ THỐNG KIỂM TRA TRẠNG THÁI THỰC TẾ (REAL-TIME)
    // ==========================================
    private fun isPowerKeeperPresent(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER ?: ""
            val brand = Build.BRAND ?: ""
            val board = Build.BOARD ?: ""
            
            val isXiaomiOrHyperOS = manufacturer.contains("Xiaomi", ignoreCase = true) ||
                                     brand.contains("Xiaomi", ignoreCase = true) ||
                                     brand.contains("Redmi", ignoreCase = true) ||
                                     brand.contains("POCO", ignoreCase = true) ||
                                     board.contains("qcom", ignoreCase = true) ||
                                     board.contains("mtk", ignoreCase = true)

            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binderPower = getService.invoke(null, "powerkeeper")
            val binderMillet = getService.invoke(null, "millet")

            isXiaomiOrHyperOS || binderPower != null || binderMillet != null
        } catch (e: Exception) {
            val brand = Build.BRAND ?: ""
            brand.contains("Xiaomi", ignoreCase = true) || brand.contains("Redmi", ignoreCase = true)
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

    private fun canWriteSettings(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(this)
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
        val hasWritePermission = canWriteSettings()

        try {
            // 1. Cập nhật Màn hình chính dựa trên trạng thái thực tế
            if (::txtMainStatusTitle.isInitialized) {
                if (hasPowerKeeper && hasWritePermission) {
                    txtMainStatusTitle.text = "Bảo vệ GMS thành công"
                    txtMainGmsBadge.text = "✓ GMS KHÔNG HẠN CHẾ • HOẠT ĐỘNG"
                    txtMainGmsBadge.setTextColor(Color.parseColor("#385723"))
                    (txtMainGmsBadge.parent as? LinearLayout)?.background = createRoundedDrawable("#E2F0D9", 15f)

                    txtMainGreezerBadge.text = "✓ BẢO VỆ GREEZER • SẴN SÀNG"
                    txtMainGreezerBadge.setTextColor(Color.parseColor("#385723"))
                    (txtMainGreezerBadge.parent as? LinearLayout)?.background = createRoundedDrawable("#E2F0D9", 15f)
                } else {
                    txtMainStatusTitle.text = "Cần cấp quyền thiết bị"
                    txtMainGmsBadge.text = "⚠ THIẾU QUYỀN HỆ THỐNG HOẶC PIN"
                    txtMainGmsBadge.setTextColor(Color.parseColor("#856404"))
                    (txtMainGmsBadge.parent as? LinearLayout)?.background = createRoundedDrawable("#FFF3CD", 15f)

                    txtMainGreezerBadge.text = "⚠ KIỂM TRA LẠI MÀN HÌNH TƯƠNG THÍCH"
                    txtMainGreezerBadge.setTextColor(Color.parseColor("#856404"))
                    (txtMainGreezerBadge.parent as? LinearLayout)?.background = createRoundedDrawable("#FFF3CD", 15f)
                }
            }

            // 2. Cập nhật Màn hình kiểm tra tương thích
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

            if (::txtWriteSettingsStatus.isInitialized) {
                if (hasWritePermission) {
                    txtWriteSettingsStatus.text = "ĐÃ CẤP"
                    txtWriteSettingsStatus.setTextColor(Color.parseColor("#155724"))
                    (txtWriteSettingsStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
                } else {
                    txtWriteSettingsStatus.text = "CHƯA BẬT"
                    txtWriteSettingsStatus.setTextColor(Color.parseColor("#856404"))
                    (txtWriteSettingsStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#FFF3CD", 15f)
                }
            }

            if (::txtSystemPropStatus.isInitialized) {
                val propStatusText = MilletHelper.systemPropStatus
                val isAccepted = MilletHelper.isPropAccepted
                txtSystemPropStatus.text = propStatusText
                if (isAccepted) {
                    txtSystemPropStatus.setTextColor(Color.parseColor("#155724"))
                    (txtSystemPropStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
                } else {
                    txtSystemPropStatus.setTextColor(Color.parseColor("#856404"))
                    (txtSystemPropStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#FFF3CD", 15f)
                }
            }

            // Kiểm tra gói GMS và CH Play thực tế trên hệ thống
            val hasGms = try { packageManager.getPackageInfo("com.google.android.gms", 0); true } catch (e: Exception) { false }
            if (::txtGmsServiceStatus.isInitialized) {
                txtGmsServiceStatus.text = if (hasGms) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY"
                txtGmsServiceStatus.setTextColor(Color.parseColor(if (hasGms) "#155724" else "#721C24"))
                (txtGmsServiceStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasGms) "#D4EDDA" else "#F8D7DA", 15f)
            }

            val hasPlayStore = try { packageManager.getPackageInfo("com.android.vending", 0); true } catch (e: Exception) { false }
            if (::txtPlayStoreStatus.isInitialized) {
                txtPlayStoreStatus.text = if (hasPlayStore) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY"
                txtPlayStoreStatus.setTextColor(Color.parseColor(if (hasPlayStore) "#155724" else "#721C24"))
                (txtPlayStoreStatus.parent as? LinearLayout)?.background = createRoundedDrawable(if (hasPlayStore) "#D4EDDA" else "#F8D7DA", 15f)
            }

            if (::txtJobSchedulerStatus.isInitialized) {
                txtJobSchedulerStatus.text = "ĐANG HOẠT ĐỘNG"
                txtJobSchedulerStatus.setTextColor(Color.parseColor("#155724"))
                (txtJobSchedulerStatus.parent as? LinearLayout)?.background = createRoundedDrawable("#D4EDDA", 15f)
            }

        } catch (e: Exception) {
            // Bỏ qua lỗi nhỏ khi view chưa sẵn sàng
        }
    }

    // ==========================================
    // 1. DỰNG MÀN HÌNH CHÍNH (ĐỘNG 100%)
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

        // Card 1: Trạng thái bảo vệ (Động theo thực tế)
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

        val badgeProtected = createBadge("TRẠNG THÁI THỰC TẾ", "#D4EDDA", "#155724")

        txtMainStatusTitle = TextView(this).apply {
            text = "Đang quét hệ thống..."
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
        
        txtMainGmsBadge = createBadge("✓ GMS KHÔNG HẠN CHẾ • ĐANG KIỂM TRA", "#E2F0D9", "#385723")
        txtMainGreezerBadge = createBadge("✓ BẢO VỆ GREEZER • ĐANG KIỂM TRA", "#E2F0D9", "#385723")

        badgeListLayout.addView(txtMainGmsBadge)
        badgeListLayout.addView(txtMainGreezerBadge)

        statusCard.addView(checkIcon)
        statusCard.addView(badgeProtected)
        statusCard.addView(txtMainStatusTitle)
        statusCard.addView(statusDesc)
        statusCard.addView(badgeListLayout)
        bodyLayout.addView(statusCard)

        // Card 2: Cấp quyền hệ thống quan trọng
        val permCard = createCardView()
        permCard.addView(TextView(this).apply {
            text = "Cài đặt & Quyền hệ thống"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        })

        permCard.addView(TextView(this).apply {
            text = "Cấp quyền thay đổi cài đặt và bỏ qua tối ưu pin để đảm bảo ứng dụng không bị hệ thống bóp ngầm."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 0, 0, 15)
        })

        val btnWriteSettings = Button(this).apply {
            text = "Cấp quyền thay đổi cài đặt hệ thống"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createRoundedDrawable("#383B8E", 20f)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                openWriteSettingsAccess()
            }
        }
        permCard.addView(btnWriteSettings)

        val btnBattery = Button(this).apply {
            text = "Yêu cầu bỏ qua tối ưu hóa pin"
            isAllCaps = false
            setTextColor(Color.parseColor("#383B8E"))
            textSize = 14f
            background = createRoundedDrawable("#E8E9F5", 20f)
            setPadding(0, 20, 0, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 0) }
            setOnClickListener {
                requestBatteryOptimization()
            }
        }
        permCard.addView(btnBattery)
        bodyLayout.addView(permCard)

        // Card 3: Nâng cao
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

        val isAurogonEnabled = sharedPreferences.getBoolean("pref_aurogon_allowlist", true)

        val switchToggle = Switch(this).apply {
            isChecked = isAurogonEnabled
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit().putBoolean("pref_aurogon_allowlist", isChecked).apply()
                val msg = if (isChecked) "Đã bật Aurogon Allowlist" else "Đã tắt Aurogon Allowlist"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

        switchLayout.addView(switchLeft)
        switchLayout.addView(switchToggle)

        advancedCard.addView(advTitle)
        advancedCard.addView(switchLayout)
        bodyLayout.addView(advancedCard)

        // Card 4: Kiểm tra tương thích
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
    // 2. DỰNG MÀN HÌNH KIỂM TRA TƯƠNG THÍCH (THỰC TẾ 100%)
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
            text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
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
            text = "Trạng thái Hệ thống & Quyền"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        // Khởi tạo các nhãn trạng thái động thực tế
        val hasPK = isPowerKeeperPresent()
        txtPowerKeeperStatus = createBadge(if (hasPK) "ĐÃ PHÁT HIỆN" else "KHÔNG PHÁT HIỆN", if (hasPK) "#D4EDDA" else "#F8D7DA", if (hasPK) "#155724" else "#721C24")
        hkCard.addView(createCustomRow("PowerKeeper / Millet Service", txtPowerKeeperStatus))

        val hasWrite = canWriteSettings()
        txtWriteSettingsStatus = createBadge(if (hasWrite) "ĐÃ CẤP" else "CHƯA BẬT", if (hasWrite) "#D4EDDA" else "#FFF3CD", if (hasWrite) "#155724" else "#856404")
        hkCard.addView(createCustomRow("Quyền thay đổi cài đặt", txtWriteSettingsStatus))

        val isBattery = isIgnoringBatteryOptimizations()
        txtBatteryStatus = createBadge(if (isBattery) "ĐÃ XÁC MINH" else "CHƯA BẬT", if (isBattery) "#D4EDDA" else "#FFF3CD", if (isBattery) "#155724" else "#856404")
        hkCard.addView(createCustomRow("Miễn tối ưu pin Android", txtBatteryStatus))

        txtSystemPropStatus = createBadge(MilletHelper.systemPropStatus, if (MilletHelper.isPropAccepted) "#D4EDDA" else "#FFF3CD", if (MilletHelper.isPropAccepted) "#155724" else "#856404")
        hkCard.addView(createCustomRow("Ghi cờ SystemProperties", txtSystemPropStatus))

        // Các dòng kiểm tra gói dịch vụ thực tế
        val hasGms = try { packageManager.getPackageInfo("com.google.android.gms", 0); true } catch (e: Exception) { false }
        txtGmsServiceStatus = createBadge(if (hasGms) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY", if (hasGms) "#D4EDDA" else "#F8D7DA", if (hasGms) "#155724" else "#721C24")
        hkCard.addView(createCustomRow("Google Play services", txtGmsServiceStatus))

        val hasPlayStore = try { packageManager.getPackageInfo("com.android.vending", 0); true } catch (e: Exception) { false }
        txtPlayStoreStatus = createBadge(if (hasPlayStore) "ĐÃ CÀI ĐẶT" else "KHÔNG TÌM THẤY", if (hasPlayStore) "#D4EDDA" else "#F8D7DA", if (hasPlayStore) "#155724" else "#721C24")
        hkCard.addView(createCustomRow("CH Play", txtPlayStoreStatus))

        txtJobSchedulerStatus = createBadge("ĐANG HOẠT ĐỘNG", "#D4EDDA", "#155724")
        hkCard.addView(createCustomRow("Tiến trình JobScheduler", txtJobSchedulerStatus))

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

    // ==========================================
    // CÁC HÀM XỬ LÝ MỞ CÀI ĐẶT QUYỀN HỆ THỐNG
    // ==========================================
    private fun openWriteSettingsAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt hệ thống", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Thiết bị không hỗ trợ tính năng này", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt pin: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isCompatibilityScreen) {
            setContentView(mainScrollView)
            isCompatibilityScreen = false
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
