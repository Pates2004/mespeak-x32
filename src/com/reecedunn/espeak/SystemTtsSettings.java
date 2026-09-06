package com.reecedunn.espeak;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

final class SystemTtsSettings {
    private SystemTtsSettings() { }

    static boolean launch(Context context) {
        // OEMs may omit or protect the TTS activity. Each fallback needs the
        // same protection, and Settings belongs in its own task in Recents.
        for (String action : new String[] {
                "com.android.settings.TTS_SETTINGS",
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
                Settings.ACTION_SETTINGS }) {
            try {
                context.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                // Try the next publicly accessible settings screen.
            }
        }
        return false;
    }

    static void open(Context context) {
        if (!launch(context)) {
            Toast.makeText(context, R.string.system_tts_settings_unavailable, Toast.LENGTH_LONG).show();
        }
    }
}
