package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nếu dự án của bạn sử dụng layout UI, thay R.layout.activity_main bằng layout thực tế
        setContentView(R.layout.activity_main)

        // 1. Áp dụng cờ Millet ngay khi mở ứng dụng
        applyMilletProtection(this)

        // 2. Lập lịch JobScheduler chạy ngầm định kỳ duy trì bảo vệ
        scheduleMilletJob(this)

        // 3. Khởi chạy ForegroundService duy trì tiến trình chính
        startProtectionService(this)

        Toast.makeText(this, "Đã kích hoạt bảo vệ GMS/Millet thành công!", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "GMSProtect"
        private const val JOB_ID = 10086
        private const val MILLET_NO_RESTRICT_APP = "MILLET_NO_RESTRICT_APP"

        /**
         * Gọi API ẩn Millet của HyperOS/MIUI thông qua Reflection
         */
        fun applyMilletProtection(context: Context) {
            try {
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)
                val binder = getService.invoke(null, "millet")

                if (binder != null) {
                    Log.d(TAG, "Đã kết nối dịch vụ Millet. Đang áp dụng cờ: $MILLET_NO_RESTRICT_APP")
                    
                    val systemProperties = Class.forName("android.os.SystemProperties")
                    val setProp = systemProperties.getMethod("set", String::class.java, String::class.java)
                    setProp.invoke(null, "persist.sys.millet.gms", "1")
                    
                    Log.i(TAG, "Áp dụng cờ Millet thành công!")
                } else {
                    Log.w(TAG, "Dịch vụ Millet không tồn tại trên thiết bị này.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi gọi API Millet: ${e.message}", e)
            }
        }

        /**
         * Đăng ký JobScheduler chạy định kỳ mỗi 15 phút
         */
        fun scheduleMilletJob(context: Context) {
            val componentName = ComponentName(context, MilletWatchJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60 * 1000L) // 15 phút lập lịch 1 lần
                .setPersisted(true)           // Tự khôi phục sau khi khởi động lại máy
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val resultCode = jobScheduler.schedule(builder.build())

            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "Đã lập lịch MilletWatchJobService thành công!")
            } else {
                Log.e(TAG, "Lập lịch MilletWatchJobService thất bại!")
            }
        }

        /**
         * Khởi chạy Foreground Service
         */
        fun startProtectionService(context: Context) {
            try {
                val serviceIntent = Intent(context, MyForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi khởi chạy ForegroundService: ${e.message}")
            }
        }
    }
}

/**
 * JobService ngầm định kỳ gọi lại hàm kích hoạt Millet
 */
class MilletWatchJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MilletWatchJobService", "Thực thi kiểm tra và kích hoạt lại Millet...")
        
        // Thực thi bảo vệ Millet
        MainActivity.applyMilletProtection(applicationContext)

        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Yêu cầu hệ thống lập lịch lại nếu bị ngắt đột ngột
    }
}
