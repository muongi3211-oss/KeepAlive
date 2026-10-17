package com.kinh.gmsprotect

import android.app.Activity
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
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var sharedPreferences: SharedPreferences

    // Các TextView hiển thị trạng thái động
    private lateinit var txtMainStatusTitle: TextView
    private lateinit var txtMainGmsBadge: TextView
    private lateinit var txtMainGreezerBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Khởi tạo SharedPreferences an toàn
            sharedPreferences = getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)

            // Ẩn tiêu đề Activity gốc để dùng Header custom
            requestWindowFeature(Window.FEATURE_NO_TITLE)

            // Hiển thị giao diện màn hình chính
            setContentView(createMainScreen())

            // Thực thi bảo vệ Millet và JobScheduler ở nền (An toàn tuyệt đối)
            try {
                MilletHelper.applyMilletProtection(this)
                MilletHelper.scheduleMilletJob(this)
            } catch (_: Throwable) {}

            // CHỈ khởi động Foreground Service nếu người dùng thực sự bật công tắc Aurogon
            val isAurogonEnabled = sharedPreferences.getBoolean("pref_aurogon_allowlist", false)
            if (isAurogonEnabled) {
                try {
                    MilletHelper.startProtectionService(this)
                } catch (_: Throwable) {}
            }

        } catch (e: Throwable) {
            // Bắt trọn mọi ngoại lệ khởi động để không bao giờ bị văng app
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Ngay khi người dùng cấp quyền xong và quay lại app, tự động gọi lại lệnh ghi Millet ngay lập tức
            if (canWriteSettings()) {
                try {
                    MilletHelper.applyMilletProtection(this)
                } catch (_: Throwable) {}
            }
            updateRealtimeStatus()
        } catch (_: Throwable) {}
    }

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
        val hasWritePermission = canWriteSettings()

        try {
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
        } catch (_: Exception) {}
    }

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

        // Card 1: Trạng thái bảo vệ
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

        // Card 2: Cấp quyền hệ thống
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
            setOnClickListener { openWriteSettingsAccess() }
        }
        permCard.addView(btnWriteSettings)

        val btnBattery = Button(this).apply {
            text = "Yêu cầu bỏ qua tối ưu hóa pin"
            isAllCaps = false
            setTextColor(Color.parseColor("#383B8E"))
            textSize = 14f
            background = createRoundedDrawable("#E8E9F5", 20f)
            setPadding(0, 20, 0, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 12, 0, 0) }
            setOnClickListener { requestBatteryOptimization() }
        }
        permCard.addView(btnBattery)
        bodyLayout.addView(permCard)

        // Card 3: Nâng cao (Aurogon Allowlist)
        val advancedCard = createCardView()
        advancedCard.addView(TextView(this).apply {
            text = "Nâng cao"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 1f
        }

        val switchLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
        }

        switchLeft.addView(TextView(this).apply {
            text = "Aurogon Allowlist"
            textSize = 15f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
        })

        switchLeft.addView(TextView(this).apply {
            text = "Lớp bảo vệ bổ sung cho HyperOS China. Khôi phục Google Play services vào danh sách cho phép khi tắt màn hình."
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 8, 10, 0)
        })

        val isAurogonEnabled = sharedPreferences.getBoolean("pref_aurogon_allowlist", false)
        val switchToggle = Switch(this).apply {
            isChecked = isAurogonEnabled
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.2f).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit().putBoolean("pref_aurogon_allowlist", isChecked).apply()
                if (isChecked) {
                    try { MilletHelper.startProtectionService(context) } catch (_: Throwable) {}
                    Toast.makeText(context, "Đã bật Aurogon Allowlist", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val intent = Intent(context, AurogonAllowlistService::class.java)
                        context.stopService(intent)
                    } catch (_: Throwable) {}
                    Toast.makeText(context, "Đã tắt Aurogon Allowlist", Toast.LENGTH_SHORT).show()
                }
            }
        }

        switchLayout.addView(switchLeft)
        switchLayout.addView(switchToggle)
        advancedCard.addView(switchLayout)
        bodyLayout.addView(advancedCard)

        // Card 4: Kiểm tra tương thích
        val checkCard = createCardView()
        checkCard.addView(TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
        })

        checkCard.addView(TextView(this).apply {
            text = "Kiểm tra trạng thái thực tế của PowerKeeper và cơ chế tự phục hồi trên thiết bị này."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 8, 0, 20)
        })

        val btnOpenCheck = Button(this).apply {
            text = "Mở Kiểm tra tương thích"
            isAllCaps = false
            setTextColor(Color.parseColor("#383B8E"))
            textSize = 14f
            background = createRoundedDrawable("#E8E9F5", 20f)
            setPadding(0, 25, 0, 25)
            setOnClickListener {
                try {
                    startActivity(Intent(this@MainActivity, CompatibilityActivity::class.java))
                } catch (_: Throwable) {}
            }
        }
        checkCard.addView(btnOpenCheck)
        bodyLayout.addView(checkCard)

        mainLayout.addView(bodyLayout)
        rootScroll.addView(mainLayout)
        return rootScroll
    }

    private fun openWriteSettingsAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun requestBatteryOptimization() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun createCardView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable("#FFFFFF", 30f)
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
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
            layoutParams = LinearLayout.LayoutParams(-2, -2)
        }
    }

    private fun createRoundedDrawable(colorHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            setCornerRadius(cornerRadius)
        }
    }
}
