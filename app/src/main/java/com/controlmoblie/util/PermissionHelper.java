package com.controlmoblie.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

public class PermissionHelper {
    private static final String TAG = "PermissionHelper";

    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    public static Intent createOverlaySettingsIntent(Activity activity) {
        return new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + activity.getPackageName())
        );
    }

    public static boolean hasRecordPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String expected = context.getPackageName() + "/" + context.getPackageName() + ".service.ControlAccessibilityService";
        try {
            String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabled == null) return false;
            Log.d(TAG, "enabled services: " + enabled);
            TextUtils.SimpleStringSplitter colonSplit = new TextUtils.SimpleStringSplitter(':');
            colonSplit.setString(enabled);
            while (colonSplit.hasNext()) {
                String service = colonSplit.next();
                if (service.equalsIgnoreCase(expected)) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check accessibility service", e);
        }
        return false;
    }

    public static Intent createAccessibilitySettingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }
}
