package com.reecedunn.espeak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Keeps the Recents task rooted in settings, even when the alias is hidden. */
public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, TtsSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
