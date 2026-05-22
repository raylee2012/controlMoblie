package com.controlmoblie.overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object PermissionHelper {

    private const val TAG = "PermissionHelper"

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun createOverlaySettingsIntent(activity: Activity): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
    }

    fun hasRecordPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${context.packageName}.service.ControlAccessibilityService"
        try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            Log.d(TAG, "enabled services: $enabled")
            val colonSplit = TextUtils.SimpleStringSplitter(':')
            colonSplit.setString(enabled)
            while (colonSplit.hasNext()) {
                val service = colonSplit.next()
                if (service.equals(expected, ignoreCase = true)) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check accessibility service", e)
        }
        return false
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}