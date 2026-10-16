package com.kinh.gmsprotect

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log

class MilletWatchJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MilletWatchJobService", "Thực thi kiểm tra và kích hoạt lại Millet...")
        
        // Chạy lại cờ bảo vệ
        MilletManager.applyMilletProtection(applicationContext)

        // Hoàn thành Job
        jobFinished(params, false)
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // Trả về true để hệ thống tự động lập lịch lại nếu Job bị ngắt đột ngột
        return true
    }

    companion object {
        private const val JOB_ID = 10086

        /**
         * Lập lịch chạy định kỳ JobService mỗi 15 phút
         */
        fun scheduleJob(context: Context) {
            val componentName = ComponentName(context, MilletWatchJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60 * 1000L) // Chạy lặp lại mỗi 15 phút
                .setPersisted(true) // Duy trì sau khi khởi động lại máy
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)

            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val resultCode = jobScheduler.schedule(builder.build())

            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.i("MilletWatchJobService", "Đã lập lịch MilletWatchJobService thành công!")
            } else {
                Log.e("MilletWatchJobService", "Lập lịch MilletWatchJobService thất bại!")
            }
        }
    }
}
