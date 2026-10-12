package com.cyanrbt.walkbuddy;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.widget.Toast;

/** Capability and user setting are separate; neither is an overlay permission. */
final class PipSupport {
    private PipSupport() {}

    static boolean supported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    static boolean available(Context context) {
        if (!supported(context)) return false;
        try {
            AppOpsManager ops = context.getSystemService(AppOpsManager.class);
            return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException error) {
            return false;
        }
    }

    static void openSettings(Activity activity) {
        // The dedicated Settings action is not a public SDK constant; OEMs may omit it.
        Intent intent = new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
            Uri.parse("package:" + activity.getPackageName()));
        try { activity.startActivity(intent); }
        catch (RuntimeException error) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
            } catch (RuntimeException unavailable) {
                Toast.makeText(activity, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }
}
