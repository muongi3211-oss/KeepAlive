package com.example.fcmguard

import android.content.Context
import android.text.format.DateFormat

object LogStore {
    private const val PREF_NAME = "fcm_guard_logs"
    private const val KEY_LOGS = "logs_history"
    
    var onNewLog: (() -> Unit)? = null

    fun getLogs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedString = prefs.getString(KEY_LOGS, "") ?: ""
        return if (savedString.isEmpty()) emptyList() else savedString.split("\n---LOG_SEP---\n")
    }

    fun add(context: Context, message: String) {
        val time = DateFormat.format("HH:mm:ss (dd/MM)", System.currentTimeMillis())
        val newLog = "[$time] $message"
        
        val currentLogs = getLogs(context).toMutableList()
        currentLogs.add(0, newLog)
        
        if (currentLogs.size > 50) {
            currentLogs.removeAt(currentLogs.size - 1)
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOGS, currentLogs.joinToString("\n---LOG_SEP---\n")).apply()

        onNewLog?.invoke()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        onNewLog?.invoke()
    }
}
