package com.tvlauncher.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Attempts to open TV Launcher PG after Android has completed booting. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                // Launch as soon as Android broadcasts that boot has completed.
                // There is no extra artificial delay; Android/firmware startup timing still applies.
                Intent launch = new Intent(appContext, MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (Build.VERSION.SDK_INT >= 26) {
                    // Android 8/9 normally allows this from BOOT_COMPLETED; the device
                    // firmware may still impose its own startup restrictions.
                    appContext.startActivity(launch);
                } else {
                    appContext.startActivity(launch);
                }
            } catch (Exception ignored) {
                // Some TV-box firmwares block activity launches during boot.
            } finally {
                pending.finish();
            }
        }, "TVLauncherPG-Boot").start();
    }
}
