package com.kinh.gmsprotect

import android.content.Context

object CompatibilityVerification {
    fun markWatcherTriggered(context: Context) {}
    fun markAutoRepairVerified(context: Context) {}
    fun markHyperOsProtectionRequestSent(context: Context) {}
    fun hyperOsProtectionRequestSent(context: Context): Boolean = false
    fun watcherVerified(context: Context): Boolean = false
    fun autoRepairVerified(context: Context): Boolean = false
    fun needsReverificationAfterSystemUpdate(context: Context): Boolean = false
    fun lastVerifiedTime(context: Context): Long = 0L
}
