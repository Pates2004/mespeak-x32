package com.reecedunn.espeak;

import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceFragment;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.reecedunn.espeak.preference.ImportVoicePreference;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImportLifecycleRegressionTest {
    @Test public void successfulImportReportsToRecreatedActivityAndRestoresDialog() throws Exception {
        recreateDuringImport(false);
    }
    @Test public void providerFailureReportsToRecreatedActivityAndKeepsDictionary() throws Exception {
        recreateDuringImport(true);
    }

    private Uri document() {
        return Uri.parse("content://" + InstrumentationRegistry.getInstrumentation().getContext()
            .getPackageName() + ".dictionary-test/pl.dict");
    }
    private void awaitPreferences(ActivityScenario<TtsSettingsActivity> screen) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 100 && !ready.get(); ++i) {
            screen.onActivity(activity -> {
                PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
                ready.set(fragment != null && fragment.findPreference("speech_preview") != null);
            });
            if (!ready.get()) Thread.sleep(50);
        }
        assertTrue("Settings must load", ready.get());
    }
    private void awaitMessage(ActivityScenario<TtsSettingsActivity> screen, boolean failure) throws Exception {
        AtomicBoolean shown = new AtomicBoolean();
        for (int i = 0; i < 100 && !shown.get(); ++i) {
            screen.onActivity(activity -> {
                DialogFragment result = (DialogFragment) activity.getFragmentManager()
                    .findFragmentByTag("dictionary_import_result");
                if (result == null || result.getDialog() == null) return;
                TextView message = result.getDialog().findViewById(android.R.id.message);
                if (message == null || !message.isShown()) return;
                assertEquals(activity.getString(failure ? R.string.import_voice_failed : R.string.import_voice_succeeded),
                    message.getText().toString());
                assertNotEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, message.getImportantForAccessibility());
                shown.set(true);
            });
            if (!shown.get()) Thread.sleep(50);
        }
        assertTrue("Import outcome must remain visible after recreation", shown.get());
    }

    private void recreateDuringImport(boolean failure) throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context provider = InstrumentationRegistry.getInstrumentation().getContext();
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        File active = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
        File retained = new File(storage.getDir("imported_dictionaries", 0), "pl_dict");
        byte[] original = FileUtils.readBinary(active);
        byte[] originalRetained = retained.isFile() ? FileUtils.readBinary(retained) : null;
        try (OutputStream output = provider.getContentResolver().openOutputStream(document())) {
            assertNotNull(output);
            output.write(original); // A real compatible dictionary, never malformed native input.
        }
        provider.getContentResolver().call(document(), "holdRead", failure ? "failure" : "success", null);
        ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class);
        AtomicReference<Fragment> owner = new AtomicReference<>();
        try {
            awaitPreferences(screen);
            screen.onActivity(activity -> {
                activity.onActivityResult(ImportVoicePreference.REQUEST_IMPORT_DICTIONARY,
                    android.app.Activity.RESULT_OK, new Intent().setData(document()));
                owner.set(activity.getFragmentManager().findFragmentByTag("dictionary_import_operation"));
            });
            assertNotNull(owner.get());
            boolean entered = false;
            for (int i = 0; i < 100 && !entered; ++i) {
                entered = provider.getContentResolver().call(document(), "readEntered", null, null)
                    .getBoolean("entered");
                if (!entered) Thread.sleep(50);
            }
            assertTrue("Import must actually be in progress before recreation", entered);
            screen.recreate();
            awaitPreferences(screen);
            screen.onActivity(activity -> assertSame(owner.get(),
                activity.getFragmentManager().findFragmentByTag("dictionary_import_operation")));
            provider.getContentResolver().call(document(), "releaseRead", null, null);
            awaitMessage(screen, failure);
            assertArrayEquals(original, FileUtils.readBinary(active));
            if (!failure) assertArrayEquals(original, FileUtils.readBinary(retained));
            screen.recreate();
            awaitMessage(screen, failure);
        } finally {
            provider.getContentResolver().call(document(), "releaseRead", null, null);
            if (owner.get() != null) {
                Field worker = DictionaryImportFragment.class.getDeclaredField("worker");
                worker.setAccessible(true);
                Thread task = (Thread) worker.get(owner.get());
                if (task != null) {
                    task.join(5000);
                    assertFalse("Import worker must finish before restoring the original profile", task.isAlive());
                }
            }
            synchronized (CheckVoiceData.class) {
                synchronized (CoreState.LOCK) {
                    FileUtils.write(active, original);
                    if (originalRetained == null) assertTrue(!retained.exists() || retained.delete());
                    else FileUtils.write(retained, originalRetained);
                    CoreState.invalidateVoice();
                }
            }
            provider.getContentResolver().call(document(), "resetRead", null, null);
            provider.getContentResolver().delete(document(), null, null);
            screen.close();
        }
    }
}
