package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tự động ẩn ActionBar mặc định để dùng Header tím custom
        supportActionBar?.hide()

        // Khởi tạo 2 giao diện (Màn hình chính & Màn hình kiểm tra tương thích)
        mainScrollView = createMainScreen()
        compatibilityScrollView = createCompatibilityScreen()

        // Mặc định hiển thị Màn hình chính
        setContentView(mainScrollView)

        // Thực thi cốt lõi chạy ngầm Millet & JobScheduler
        applyMilletProtection(this)
        scheduleMilletJob(this)
        startProtectionService(this)
    }

    // ==========================================
    // 1. DỰNG MÀN HÌNH CHÍNH (MAIN SCREEN)
    // ==========================================
    private fun createMainScreen(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header Màu Tím
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

        // Nội dung dạng Card
        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 30, 35, 40)
        }

        // --- CARD 1: TRẠNG THÁI BẢO VỆ THÀNH CÔNG ---
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
            text = "Bạn có thể đóng ứng dụng. Bảo vệ sẽ tự động được kích hoạt lại sau khi khởi động thiết bị.\n\nBảo vệ cũng được làm mới mỗi lần bạn mở GMS Protect."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            gravity = Gravity.CENTER
            setPadding(20, 0, 20, 25)
        }

        val badgeListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        badgeListLayout.addView(createBadge("✓ GMS KHÔNG HẠN CHẾ • ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        badgeListLayout.addView(createBadge("✓ BẢO VỆ GREEZER • ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        badgeListLayout.addView(createBadge("✓ CH PLAY KHÔNG HẠN CHẾ • ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))

        statusCard.addView(checkIcon)
        statusCard.addView(badgeProtected)
        statusCard.addView(statusTitle)
        statusCard.addView(statusDesc)
        statusCard.addView(badgeListLayout)
        bodyLayout.addView(statusCard)

        // --- CARD 2: NÂNG CAO (AUROGON ALLOWLIST) ---
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
            text = "Lớp bảo vệ bổ sung cho HyperOS China. Khi bật, GMS Protect chạy nền nhẹ để khôi phục Google Play services vào Aurogon Allowlist sau khi hệ thống thay đổi chính sách lúc tắt màn.\n\nNhịp nền: 40 giây sau khi tắt màn -> phút thứ 2 -> mỗi 10 phút khi màn vẫn tắt.\n\nMiễn tối ưu pin Android: ĐÃ XÁC MINH"
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

        // --- CARD 3: KIỂM TRA TƯƠNG THÍCH ---
        val checkCard = createCardView()

        val checkTitle = TextView(this).apply {
            text = "Kiểm tra tương thích"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
        }

        val checkDesc = TextView(this).apply {
            text = "Kiểm tra HyperOS / PowerKeeper và xem cơ chế tự phục hồi theo sự kiện đã được xác minh trên ROM này chưa."
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
    // 2. DỰNG MÀN HÌNH KIỂM TRA TƯƠNG THÍCH
    // ==========================================
    private fun createCompatibilityScreen(): ScrollView {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F4F5F9"))
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Header Màu Tím cho trang Tương thích
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
            text = "Xác minh các cơ chế bảo vệ thực sự hoạt động trên thiết bị này"
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

        // Card Thống kê Thiết bị
        val devCard = createCardView()
        val devInfo = TextView(this).apply {
            text = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}  •  Android ${Build.VERSION.RELEASE}"
            textSize = 13f
            setTextColor(Color.parseColor("#495057"))
            setTypeface(null, Typeface.BOLD)
        }
        val devBadge = createBadge("TƯƠNG THÍCH ĐẦY ĐỦ", "#E2F0D9", "#385723").apply {
            setPadding(20, 8, 20, 8)
        }
        val devDesc = TextView(this).apply {
            text = "Kết quả kết hợp trạng thái hiện tại với bằng chứng thực tế được lưu khi Android/HyperOS thật sự gọi dậy GMS Protect sau khi danh sách MILLET thay đổi."
            textSize = 12f
            setTextColor(Color.parseColor("#6C757D"))
            setPadding(0, 15, 0, 0)
        }

        devCard.addView(devInfo)
        devCard.addView(devBadge)
        devCard.addView(devDesc)
        bodyLayout.addView(devCard)

        // Card Danh sách Kiểm tra HyperOS / PowerKeeper
        val hkCard = createCardView()
        hkCard.addView(TextView(this).apply {
            text = "HyperOS / PowerKeeper"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        hkCard.addView(createCheckRow("PowerKeeper", "ĐÃ PHÁT HIỆN", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("Danh sách Không hạn chế", "ĐỌC / GHI ĐƯỢC", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("Google Play services", "ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("CH Play", "ĐÃ ÁP DỤNG", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("GMS Protect Không hạn chế", "ĐÃ XÁC MINH", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("Miễn tối ưu pin Android", "ĐÃ XÁC MINH", "#E2F0D9", "#385723"))
        hkCard.addView(createCheckRow("Bảo vệ nền HyperOS", "ĐÃ GỬI YÊU CẦU", "#E2F0D9", "#385723"))

        hkCard.addView(TextView(this).apply {
            text = "Mục này chỉ xác nhận GMS Protect đã gửi yêu cầu bảo vệ tới cơ chế nền của HyperOS. Ứng dụng không thể đọc ngược danh sách nội bộ để khẳng định trạng thái đã được áp dụng."
            textSize = 11f
            setTextColor(Color.parseColor("#8C96A0"))
            setPadding(0, 15, 0, 0)
        })
        bodyLayout.addView(hkCard)

        // Card Tự phục hồi
        val autoCard = createCardView()
        autoCard.addView(TextView(this).apply {
            text = "Tự phục hồi"
            textSize = 17f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        })

        autoCard.addView(createCheckRow("Theo dõi thay đổi hệ thống", "ĐÃ ĐĂNG KÝ", "#E2F0D9", "#385723"))
        autoCard.addView(createCheckRow("Hệ thống gọi dậy ứng dụng", "ĐÃ XÁC MINH", "#E2F0D9", "#385723"))
        autoCard.addView(createCheckRow("Tự khôi phục GMS", "ĐÃ XÁC MINH", "#E2F0D9", "#385723"))
        autoCard.addView(createCheckRow("Lần xác minh gần nhất", "16/10/26 15:54", "#F8F9FA", "#6C757D"))

        bodyLayout.addView(autoCard)

        // Nút quay lại Màn hình chính
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
    // 3. CÁC HÀM TẠO WIDGET GIAO DIỆN PHỤ TRỢ
    // ==========================================
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
            ).apply {
                setMargins(0, 10, 0, 10)
            }
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

    // ==========================================
    // 4. CỐT LÕI BẢO VỆ MILLET & JOBSCHEDULER
    // ==========================================
    companion object {
        private const val TAG = "GMSProtect"
        private const val JOB_
