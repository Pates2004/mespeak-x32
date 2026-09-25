package com.reecedunn.espeak;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SettingsRegressionTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void importedDictionarySurvivesBundledDataRefresh() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        File source = new File(context.getCacheDir(), "af_dict");
        File installed = new File(CheckVoiceData.getDataPath(storage), source.getName());
        byte[] bundled = FileUtils.readBinary(installed);
        byte[] contents = "imported dictionary sample".getBytes(StandardCharsets.UTF_8);
        try {
            try (FileOutputStream output = new FileOutputStream(source)) {
                output.write(contents);
            }
            CheckVoiceData.installImportedDictionary(storage, source);
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(contents, FileUtils.readBinary(
                    new File(CheckVoiceData.getDataPath(storage), source.getName())));
        } finally {
            new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE),
                    source.getName()).delete();
            FileUtils.write(installed, bundled);
            source.delete();
        }
    }

    @Test
    public void legacyImportedDictionarySurvivesBundledDataRefresh() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        boolean oldMarker = prefs.getBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false);
        File installed = new File(CheckVoiceData.getDataPath(storage), "zz_legacy_dict");
        File retained = new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE),
                installed.getName());
        byte[] contents = "legacy dictionary sample".getBytes(StandardCharsets.UTF_8);
        try {
            assertTrue(prefs.edit().putBoolean(
                    CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false).commit());
            FileUtils.write(installed, contents);
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(contents, FileUtils.readBinary(installed));
            assertArrayEquals(contents, FileUtils.readBinary(retained));
        } finally {
            installed.delete();
            retained.delete();
            prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED,
                    oldMarker).commit();
        }
    }

    @Test
    public void oldCredentialStorageImportMigratesToTheEngine() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        boolean oldMarker = prefs.getBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false);
        boolean oldPreserve = prefs.getBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true);
        File legacy = new File(CheckVoiceData.getDataPath(context), "zz_credential_dict");
        File installed = new File(CheckVoiceData.getDataPath(storage), legacy.getName());
        assertNotEquals("Credential and device storage must differ",
                legacy.getCanonicalPath(), installed.getCanonicalPath());
        File retained = new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE),
                legacy.getName());
        byte[] contents = "credential dictionary sample".getBytes(StandardCharsets.UTF_8);
        try {
            assertTrue(prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false)
                    .putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true).commit());
            assertTrue(legacy.getParentFile().isDirectory() || legacy.getParentFile().mkdirs());
            FileUtils.write(legacy, contents);
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(contents, FileUtils.readBinary(installed));
        } finally {
            legacy.delete();
            installed.delete();
            retained.delete();
            prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, oldMarker)
                    .putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, oldPreserve).commit();
        }
    }

    @Test
    public void turningOffPreservationRestoresBundledDictionaryOnUpdate() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        boolean oldSetting = prefs.getBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true);
        File source = new File(context.getCacheDir(), "af_dict");
        File installed = new File(CheckVoiceData.getDataPath(storage), source.getName());
        File retained = new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE),
                source.getName());
        byte[] bundled = FileUtils.readBinary(installed);
        try {
            try (FileOutputStream output = new FileOutputStream(source)) {
                output.write("temporary override".getBytes(StandardCharsets.UTF_8));
            }
            CheckVoiceData.installImportedDictionary(storage, source);
            assertTrue(retained.exists());
            assertTrue(prefs.edit().putBoolean(
                    EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, false).commit());
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(bundled, FileUtils.readBinary(installed));
            assertFalse(retained.exists());
        } finally {
            prefs.edit().putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES,
                    oldSetting).commit();
            retained.delete();
            FileUtils.write(installed, bundled);
            source.delete();
        }
    }

    @Test
    public void laterUpdatesDoNotMisclassifyBundledChangesAsImports() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        File installed = new File(CheckVoiceData.getDataPath(storage), "af_dict");
        File retained = new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE),
                installed.getName());
        byte[] bundled = FileUtils.readBinary(installed);
        boolean oldMarker = prefs.getBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false);
        boolean oldPreserve = prefs.getBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true);
        try {
            assertFalse(retained.exists());
            assertTrue(prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, true)
                    .putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true).commit());
            FileUtils.write(installed, "older bundled version".getBytes(StandardCharsets.UTF_8));
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(bundled, FileUtils.readBinary(installed));
            assertFalse(retained.exists());
        } finally {
            prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, oldMarker)
                    .putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, oldPreserve).commit();
            retained.delete();
            FileUtils.write(installed, bundled);
        }
    }

    @Test
    public void systemSettingsFallsBackWhenOemProtectsTtsActivity() {
        List<String> actions = new ArrayList<>();
        Context oem = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) {
                assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
                actions.add(intent.getAction());
                if (actions.size() == 1) throw new SecurityException("OEM restricts TTS settings");
            }
        };
        assertTrue(SystemTtsSettings.launch(oem));
        assertEquals("com.android.settings.TTS_SETTINGS", actions.get(0));
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, actions.get(1));
    }

    @Test
    public void allSettingsFallbacksCanFailWithoutCrashing() {
        AtomicInteger attempts = new AtomicInteger();
        Context unavailable = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) {
                attempts.incrementAndGet();
                throw new ActivityNotFoundException();
            }
        };
        assertFalse(SystemTtsSettings.launch(unavailable));
        assertEquals(3, attempts.get());
    }

    @Test
    public void readingVoicesDoesNotResetModulationInRunningCore() {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SpeechSynthesis engine = new SpeechSynthesis(storage, null);
        int old = engine.PitchRange.getValue();
        try {
            engine.PitchRange.setValue(27);
            SpeechSynthesis settingsEngine = new SpeechSynthesis(storage, null);
            assertFalse(settingsEngine.getAvailableVoices().isEmpty());
            assertEquals(27, engine.PitchRange.getValue());
        } finally {
            engine.PitchRange.setValue(old);
        }
    }

    private void waitForPreferences(ActivityScenario<TtsSettingsActivity> screen) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 100 && !ready.get(); i++) {
            screen.onActivity(activity -> {
                PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                        .findFragmentById(android.R.id.content);
                ready.set(fragment != null && fragment.findPreference("speech_preview") != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("Settings must finish loading", ready.get());
    }

    @Test
    public void launcherChoiceAndModulationUseTheServicePreferenceStore() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(EspeakApp.getStorageContext());
        boolean old = prefs.getBoolean(EspeakApp.PREF_SHOW_LAUNCHER, true);
        try {
            prefs.edit().putBoolean(EspeakApp.PREF_SHOW_LAUNCHER, false).commit();
            try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
                waitForPreferences(screen);
                screen.onActivity(activity -> {
                    PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                            .findFragmentById(android.R.id.content);
                    if (Build.VERSION.SDK_INT >= 24) assertTrue(fragment.getPreferenceManager().isStorageDeviceProtected());
                    CheckBoxPreference launcher = (CheckBoxPreference) fragment.findPreference(EspeakApp.PREF_SHOW_LAUNCHER);
                    assertFalse(launcher.isChecked());
                    launcher.setChecked(true);
                    assertTrue(prefs.getBoolean(EspeakApp.PREF_SHOW_LAUNCHER, false));
                    assertNotNull(fragment.findPreference(VoiceSettings.PREF_PITCH_RANGE));
                });
                screen.recreate();
                waitForPreferences(screen);
                screen.onActivity(activity -> {
                    PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                            .findFragmentById(android.R.id.content);
                    assertTrue(((CheckBoxPreference) fragment.findPreference(EspeakApp.PREF_SHOW_LAUNCHER)).isChecked());
                });
            }
        } finally {
            prefs.edit().putBoolean(EspeakApp.PREF_SHOW_LAUNCHER, old).commit();
            EspeakApp.setLauncherVisible(context, old);
        }
    }

    @Test
    public void dictionaryUpdateCheckboxIsAccessibleAndPersists() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                EspeakApp.getStorageContext());
        boolean hadSetting = prefs.contains(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES);
        boolean oldSetting = prefs.getBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true);
        try {
            prefs.edit().remove(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES).commit();
            try (ActivityScenario<TtsSettingsActivity> screen =
                    ActivityScenario.launch(TtsSettingsActivity.class)) {
                waitForPreferences(screen);
                screen.onActivity(activity -> {
                    PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                            .findFragmentById(android.R.id.content);
                    CheckBoxPreference keep = (CheckBoxPreference) fragment.findPreference(
                            EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES);
                    assertNotNull(keep);
                    assertEquals(activity.getString(R.string.preserve_imported_dictionaries_title),
                            keep.getTitle());
                    assertTrue(keep.isChecked());
                    keep.setChecked(false);
                    assertFalse(prefs.getBoolean(
                            EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true));
                });
                screen.recreate();
                waitForPreferences(screen);
                screen.onActivity(activity -> {
                    PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                            .findFragmentById(android.R.id.content);
                    CheckBoxPreference keep = (CheckBoxPreference) fragment.findPreference(
                            EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES);
                    assertFalse(keep.isChecked());
                });
            }
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (hadSetting) editor.putBoolean(
                    EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, oldSetting);
            else editor.remove(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES);
            editor.commit();
        }
    }

    @Test
    public void hiddenLauncherDoesNotDisableSettingsTask() throws Exception {
        ComponentName launcher = new ComponentName(context.getPackageName(), "com.reecedunn.espeak.Launcher");
        PackageManager manager = context.getPackageManager();
        int original = manager.getComponentEnabledSetting(launcher);
        try {
            assertTrue(EspeakApp.setLauncherVisible(context, true));
            assertTrue(EspeakApp.setLauncherVisible(context, true));
            try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
                waitForPreferences(screen);
                screen.onActivity(activity -> {
                    assertTrue(EspeakApp.setLauncherVisible(activity, false));
                    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            manager.getComponentEnabledSetting(launcher));
                    ActivityManager tasks = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                    boolean found = false;
                    for (ActivityManager.AppTask task : tasks.getAppTasks()) {
                        if (task.getTaskInfo().baseIntent.getComponent() != null
                                && task.getTaskInfo().baseIntent.getComponent().getClassName()
                                .equals(TtsSettingsActivity.class.getName())) {
                            found = true;
                        }
                    }
                    assertTrue("Recents must target the enabled settings activity", found);
                    assertTrue(EspeakApp.setLauncherVisible(activity, true));
                });
            }
        } finally {
            manager.setComponentEnabledSetting(launcher, original, PackageManager.DONT_KILL_APP);
        }
    }

    @Test
    public void previewFieldSurvivesActivityRecreation() throws Exception {
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            waitForPreferences(screen);
            showPreview(screen);
            screen.onActivity(activity -> {
                EditText editor = activity.findViewById(R.id.preview_text);
                assertNotNull(editor);
                editor.setText("Bezinteresowny człowiek.");
                assertNotNull(activity.findViewById(R.id.preview_speak));
            });
            screen.recreate();
            waitForPreferences(screen);
            showPreview(screen);
            screen.onActivity(activity -> assertEquals("Bezinteresowny człowiek.",
                    ((EditText) activity.findViewById(R.id.preview_text)).getText().toString()));
        }
    }

    private void showPreview(ActivityScenario<TtsSettingsActivity> screen) {
        screen.onActivity(activity -> {
            PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            ((ListView) fragment.getView().findViewById(android.R.id.list))
                    .setSelection(fragment.getPreferenceScreen().getPreferenceCount() - 1);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void speakButtonUsesPreviewAndStopReleasesIt() throws Exception {
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            waitForPreferences(screen);
            showPreview(screen);
            screen.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.preview_text)).setText("To jest próbka mowy.");
                activity.findViewById(R.id.preview_speak).performClick();
            });
            AtomicBoolean done = new AtomicBoolean();
            for (int i = 0; i < 200 && !done.get(); i++) {
                screen.onActivity(activity -> {
                    String status = ((TextView) activity.findViewById(R.id.preview_status)).getText().toString();
                    assertNotEquals(activity.getString(R.string.preview_failed), status);
                    done.set(activity.getString(R.string.preview_ready).equals(status));
                });
                if (!done.get()) Thread.sleep(100);
            }
            assertTrue("Preview must finish speaking", done.get());
            screen.onActivity(activity -> {
                activity.findViewById(R.id.preview_stop).performClick();
                ((EditText) activity.findViewById(R.id.preview_text)).setText("");
                activity.findViewById(R.id.preview_speak).performClick();
                assertEquals(activity.getString(R.string.preview_empty),
                        ((TextView) activity.findViewById(R.id.preview_status)).getText().toString());
            });
        }
    }

    @Test
    public void ordinaryPolishSynthesisUsesThisEngine() throws Exception {
        CountDownLatch initialized = new CountDownLatch(1);
        AtomicInteger initStatus = new AtomicInteger(TextToSpeech.ERROR);
        TextToSpeech tts = new TextToSpeech(context, result -> {
            initStatus.set(result);
            initialized.countDown();
        }, context.getPackageName());
        File wav = new File(context.getCacheDir(), "settings-synthesis.wav");
        try {
            assertTrue(initialized.await(15, TimeUnit.SECONDS));
            assertEquals(TextToSpeech.SUCCESS, initStatus.get());
            assertTrue(tts.setLanguage(new Locale("pl", "PL")) >= TextToSpeech.LANG_AVAILABLE);
            CountDownLatch done = new CountDownLatch(1);
            AtomicBoolean error = new AtomicBoolean();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { done.countDown(); }
                @Override public void onError(String id) { error.set(true); done.countDown(); }
            });
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(
                    "Bezinteresowny człowiek działa bezinteresownie.", new Bundle(), wav, "settings-check"));
            assertTrue(done.await(15, TimeUnit.SECONDS));
            assertFalse(error.get());
            assertTrue(wav.length() > 44);
        } finally {
            tts.shutdown();
            // The sample stays in this emulator app's cache, not on the phone.
        }
    }
}
