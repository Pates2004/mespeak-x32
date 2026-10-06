package com.reecedunn.espeak;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.DialogPreference;
import android.preference.PreferenceFragment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.reecedunn.espeak.preference.SeekBarPreference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RateSettingsRegressionTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final SharedPreferences preferences = context.getSharedPreferences(
            "rate_settings_regression", Context.MODE_PRIVATE);

    @Before
    public void preparePreferences() {
        assertTrue(preferences.edit().clear().commit());
    }

    @After
    public void clearPreferences() {
        assertTrue(preferences.edit().clear().commit());
    }

    @Test
    public void freshInstallationDefaultsToSmoothWithoutIgnoringAndroid() {
        assertEquals(VoiceSettings.RATE_MODE_SMOOTH, VoiceSettings.getRateMode(preferences));
        assertFalse(preferences.getBoolean(VoiceSettings.PREF_IGNORE_SYSTEM_RATE, false));
        assertEquals(175, VoiceSettings.getRateSliderValue(preferences, 175));
        assertEquals(350, VoiceSettings.getRateForCaller(preferences, 175, 200));
    }

    @Test
    public void migrationPreservesExistingStandardAndBoostSettings() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE, "200").commit());
        assertEquals(VoiceSettings.RATE_MODE_STANDARD, VoiceSettings.getRateMode(preferences));
        assertEquals(200, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.edit().remove(VoiceSettings.PREF_RATE_MODE)
                .putBoolean(VoiceSettings.PREF_RATE_BOOST, true).commit());
        assertEquals(VoiceSettings.RATE_MODE_BOOST, VoiceSettings.getRateMode(preferences));
        assertEquals(200, VoiceSettings.getRateSliderValue(preferences, 175));
        assertEquals(600, VoiceSettings.getRateForCaller(preferences, 175, 100));
    }

    @Test
    public void migrationPreservesBoostBelowTheOldSliderMinimum() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_DEFAULT_RATE, "25")
                .putBoolean(VoiceSettings.PREF_RATE_BOOST, true).commit());
        assertEquals(VoiceSettings.RATE_MODE_SMOOTH, VoiceSettings.getRateMode(preferences));
        assertEquals(129, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertFalse(preferences.getBoolean(VoiceSettings.PREF_RATE_BOOST, true));
        assertTrue(preferences.edit().clear().putString(VoiceSettings.PREF_RATE, "60")
                .putBoolean(VoiceSettings.PREF_RATE_BOOST, true).commit());
        assertEquals(VoiceSettings.RATE_MODE_SMOOTH, VoiceSettings.getRateMode(preferences));
        assertEquals(180, VoiceSettings.getRateForCaller(preferences, 175, 100));
    }

    @Test
    public void legacyPercentageBelowDefaultIsNotRaisedToDefault() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_DEFAULT_RATE, "50").commit());
        assertEquals(VoiceSettings.RATE_MODE_STANDARD, VoiceSettings.getRateMode(preferences));
        assertEquals(87, VoiceSettings.getRateSliderValue(preferences, 175));
        assertEquals(87, VoiceSettings.getRateForCaller(preferences, 175, 100));
    }

    @Test
    public void corruptedAndLargeStoredRatesNeverOverflow() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_BOOST)
                .putString(VoiceSettings.PREF_RATE, Integer.toString(Integer.MAX_VALUE)).commit());
        assertEquals(1350, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE,
                "999999999999999999999999999999").commit());
        assertEquals(1350, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_SMOOTH)
                .putString(VoiceSettings.PREF_RATE, "-999999999999999999999999999999").commit());
        assertEquals(80, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE, "invalid").commit());
        assertEquals(175, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.edit().putInt(VoiceSettings.PREF_RATE, Integer.MAX_VALUE).commit());
        assertEquals(175, VoiceSettings.getRateForCaller(preferences, 175, 100));
    }

    @Test
    public void callerScalingClampsBeforeNarrowingAndIgnoreIsIndependent() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_SMOOTH)
                .putString(VoiceSettings.PREF_RATE, "450").commit());
        assertEquals(900, VoiceSettings.getRateForCaller(preferences, 175, 200));
        assertEquals(1350, VoiceSettings.getRateForCaller(preferences, 175, Integer.MAX_VALUE));
        assertEquals(450, VoiceSettings.getRateForCaller(preferences, 175, 0));
        assertEquals(450, VoiceSettings.getRateForCaller(preferences, 175, Integer.MIN_VALUE));
        assertTrue(preferences.edit().putBoolean(VoiceSettings.PREF_IGNORE_SYSTEM_RATE, true).commit());
        assertEquals(450, VoiceSettings.getRateForCaller(preferences, 175, Integer.MAX_VALUE));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_BOOST)
                .putString(VoiceSettings.PREF_RATE, "200").commit());
        assertEquals(600, VoiceSettings.getRateForCaller(preferences, 175, Integer.MAX_VALUE));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_STANDARD)
                .putBoolean(VoiceSettings.PREF_IGNORE_SYSTEM_RATE, false).commit());
        assertEquals(450, VoiceSettings.getRateForCaller(preferences, 175, Integer.MAX_VALUE));
    }

    @Test
    public void smoothMappingCoversTheFullRangeWithoutAJavaPlateau() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_SMOOTH).commit());
        for (int target : new int[] { 80, 100, 175, 299, 300, 301, 350, 450, 451, 600, 1000, 1350 }) {
            preferences.edit().putString(VoiceSettings.PREF_RATE, Integer.toString(target)).apply();
            assertEquals(target, VoiceSettings.getRateForCaller(preferences, 175, 100));
        }
    }

    @Test
    public void modeChangesPreserveEffectiveRateWhereRepresentable() {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_STANDARD)
                .putString(VoiceSettings.PREF_RATE, "300").commit());
        assertTrue(VoiceSettings.setRateMode(preferences, VoiceSettings.RATE_MODE_SMOOTH, 175));
        assertEquals(300, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(VoiceSettings.setRateMode(preferences, VoiceSettings.RATE_MODE_BOOST, 175));
        assertEquals(100, VoiceSettings.getRateSliderValue(preferences, 175));
        assertEquals(300, VoiceSettings.getRateForCaller(preferences, 175, 100));
        assertTrue(preferences.getBoolean(VoiceSettings.PREF_RATE_BOOST, false));
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE, "450").commit());
        assertTrue(VoiceSettings.setRateMode(preferences, VoiceSettings.RATE_MODE_SMOOTH, 175));
        assertEquals(1350, VoiceSettings.getRateSliderValue(preferences, 175));
        assertTrue(VoiceSettings.setRateMode(preferences, VoiceSettings.RATE_MODE_STANDARD, 175));
        assertEquals(450, VoiceSettings.getRateSliderValue(preferences, 175));
        assertFalse(preferences.getBoolean(VoiceSettings.PREF_RATE_BOOST, true));
    }

    @Test
    public void summaryDialogAndAccessibleValueUseTheSameEffectiveRate() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences saved = PreferenceManager.getDefaultSharedPreferences(storage);
        Map<String, ?> previous = saved.getAll();
        try {
            for (String mode : new String[] { VoiceSettings.RATE_MODE_STANDARD,
                    VoiceSettings.RATE_MODE_BOOST, VoiceSettings.RATE_MODE_SMOOTH }) {
                assertTrue(saved.edit().putString(VoiceSettings.PREF_RATE_MODE, mode)
                        .putString(VoiceSettings.PREF_RATE, "200")
                        .putBoolean(VoiceSettings.PREF_RATE_BOOST, VoiceSettings.RATE_MODE_BOOST.equals(mode)).commit());
                try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
                    waitForRatePreference(screen);
                    screen.onActivity(activity -> {
                        PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                                .findFragmentById(android.R.id.content);
                        SeekBarPreference preference = (SeekBarPreference) fragment.findPreference(VoiceSettings.PREF_RATE);
                        try {
                            java.lang.reflect.Method openDialog = DialogPreference.class
                                    .getDeclaredMethod("showDialog", Bundle.class);
                            openDialog.setAccessible(true);
                            openDialog.invoke(preference, (Object) null);
                        } catch (ReflectiveOperationException error) {
                            throw new AssertionError(error);
                        }
                        Dialog dialog = preference.getDialog();
                        assertNotNull(dialog);
                        TextView value = dialog.findViewById(R.id.valueText);
                        SeekBar slider = dialog.findViewById(R.id.seekBar);
                        String expected = preference.formatValue(200);
                        assertEquals(expected, preference.getSummary().toString());
                        assertEquals(expected, value.getText().toString());
                        assertEquals(expected, slider.getContentDescription().toString());
                        assertEquals(VoiceSettings.getRateSliderMaximum(mode) - VoiceSettings.RATE_MINIMUM,
                                slider.getMax());
                        slider.setProgress(220 - VoiceSettings.RATE_MINIMUM);
                        preference.onProgressChanged(slider, slider.getProgress(), true);
                        assertEquals("220", saved.getString(VoiceSettings.PREF_RATE, null));
                        dialog.cancel();
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    assertEquals("200", saved.getString(VoiceSettings.PREF_RATE, null));
                    screen.onActivity(activity -> {
                        PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                                .findFragmentById(android.R.id.content);
                        SeekBarPreference preference = (SeekBarPreference) fragment.findPreference(VoiceSettings.PREF_RATE);
                        assertNull("Dismissal must release the framework dialog", preference.getDialog());
                    });
                }
            }
        } finally {
            restoreRatePreferences(saved, previous);
        }
    }

    @Test
    public void smoothSynthesisRemainsContinuousAtNativeBoundary() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences saved = PreferenceManager.getDefaultSharedPreferences(storage);
        Map<String, ?> previous = saved.getAll();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicInteger status = new AtomicInteger(TextToSpeech.ERROR);
        TextToSpeech tts = new TextToSpeech(context, result -> {
            status.set(result);
            ready.countDown();
        }, context.getPackageName());
        File sample = new File(context.getCacheDir(), "rate-smooth-regression.wav");
        try {
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            assertEquals(TextToSpeech.SUCCESS, status.get());
            assertTrue(tts.setLanguage(new Locale("pl", "PL")) >= TextToSpeech.LANG_AVAILABLE);
            assertEquals(TextToSpeech.SUCCESS, tts.setSpeechRate(1.0f));
            assertTrue(saved.edit().putString(VoiceSettings.PREF_RATE_MODE, VoiceSettings.RATE_MODE_SMOOTH)
                    .putBoolean(VoiceSettings.PREF_RATE_BOOST, false)
                    .putBoolean(VoiceSettings.PREF_IGNORE_SYSTEM_RATE, true).commit());
            long at300 = synthesizeLength(tts, saved, sample, 300);
            long at301 = synthesizeLength(tts, saved, sample, 301);
            assertTrue("The smooth boundary must not cause a speed jump", at301 > at300 * 0.97);
            assertTrue("The smooth boundary must not slow speech", at301 < at300 * 1.02);
            long at450 = synthesizeLength(tts, saved, sample, 450);
            long at900 = synthesizeLength(tts, saved, sample, 900);
            long at1350 = synthesizeLength(tts, saved, sample, 1350);
            assertTrue(at450 < at301);
            assertTrue(at900 < at450);
            assertTrue(at1350 < at900);
            long restored = synthesizeLength(tts, saved, sample, 300);
            assertTrue("Returning below Sonic must restore native speed",
                    Math.abs(restored - at300) <= Math.max(512L, at300 / 200));
        } finally {
            tts.shutdown();
            restoreRatePreferences(saved, previous);
            sample.delete();
        }
    }

    private static long synthesizeLength(TextToSpeech tts, SharedPreferences preferences,
                                         File sample, int rate) throws Exception {
        assertTrue(preferences.edit().putString(VoiceSettings.PREF_RATE, Integer.toString(rate)).commit());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { completed.countDown(); }
            @Override public void onError(String utteranceId) { failed.set(true); completed.countDown(); }
        });
        String text = "Pierwszy bezinteresowny człowiek spokojnie przeczytał całe zdanie, "
                + "aby sprawdzić płynną regulację szybkości oraz wyraźną wymowę.";
        assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(text, new Bundle(), sample, "smooth-" + rate));
        assertTrue("Synthesis timed out", completed.await(15, TimeUnit.SECONDS));
        assertFalse("Synthesis failed", failed.get());
        assertTrue("Expected a WAV file", sample.length() > 44);
        return sample.length();
    }

    private static void waitForRatePreference(ActivityScenario<TtsSettingsActivity> screen) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int attempt = 0; attempt < 100 && !ready.get(); attempt++) {
            screen.onActivity(activity -> {
                PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                        .findFragmentById(android.R.id.content);
                ready.set(fragment != null && fragment.findPreference(VoiceSettings.PREF_RATE) != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("Speech-rate preference did not load", ready.get());
    }

    private static void restoreRatePreferences(SharedPreferences preferences, Map<String, ?> previous) {
        SharedPreferences.Editor restore = preferences.edit();
        for (String key : new String[] { VoiceSettings.PREF_RATE, VoiceSettings.PREF_RATE_MODE,
                VoiceSettings.PREF_RATE_BOOST, VoiceSettings.PREF_IGNORE_SYSTEM_RATE }) {
            Object value = previous.get(key);
            if (value instanceof String) restore.putString(key, (String) value);
            else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) restore.putInt(key, (Integer) value);
            else restore.remove(key);
        }
        assertTrue(restore.commit());
    }
}
