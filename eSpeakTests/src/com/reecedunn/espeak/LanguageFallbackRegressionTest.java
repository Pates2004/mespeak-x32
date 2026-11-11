package com.reecedunn.espeak;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LanguageFallbackRegressionTest {
    @Test public void englishCallerUsesAvailablePolishDefaultAndSettingsPreview() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        boolean hadSelection = prefs.contains(LanguageSettings.PREF_SUPPORTED_LANGUAGES);
        Set<String> oldSelection = prefs.getStringSet(LanguageSettings.PREF_SUPPORTED_LANGUAGES, null);
        if (oldSelection != null) oldSelection = new HashSet<>(oldSelection);
        Locale oldLocale = Locale.getDefault();
        TextToSpeech speech = null;
        try {
            Locale.setDefault(Locale.ENGLISH);
            assertTrue(prefs.edit().putStringSet(LanguageSettings.PREF_SUPPORTED_LANGUAGES,
                new HashSet<>(Collections.singletonList("pol"))).commit());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            CountDownLatch initialized = new CountDownLatch(1);
            AtomicInteger result = new AtomicInteger(TextToSpeech.ERROR);
            speech = new TextToSpeech(context, status -> {
                result.set(status); initialized.countDown();
            }, context.getPackageName());
            assertTrue(initialized.await(15, TimeUnit.SECONDS));
            assertEquals(TextToSpeech.SUCCESS, result.get());
            assertTrue(speech.isLanguageAvailable(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE);
            assertTrue(speech.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE);
            assertNotNull(speech.getVoice());
            assertEquals("pol", speech.getVoice().getLocale().getISO3Language());

            try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
                AtomicBoolean ready = new AtomicBoolean();
                for (int i = 0; i < 100 && !ready.get(); ++i) {
                    screen.onActivity(activity -> {
                        PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                            .findFragmentById(android.R.id.content);
                        ready.set(fragment != null && fragment.findPreference("speech_preview") != null);
                        if (ready.get()) ((ListView) fragment.getView().findViewById(android.R.id.list))
                            .setSelection(fragment.getPreferenceScreen().getPreferenceCount() - 1);
                    });
                    if (!ready.get()) Thread.sleep(100);
                }
                assertTrue("Settings must load", ready.get());
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screen.onActivity(activity -> {
                    ((EditText) activity.findViewById(R.id.preview_text)).setText("Monika ma kota.");
                    activity.findViewById(R.id.preview_speak).performClick();
                    String status = ((TextView) activity.findViewById(R.id.preview_status)).getText().toString();
                    assertTrue("Preview must start before completion can count",
                        activity.getString(R.string.preview_starting).equals(status)
                            || activity.getString(R.string.preview_speaking).equals(status));
                });
                AtomicBoolean finished = new AtomicBoolean();
                for (int i = 0; i < 150 && !finished.get(); ++i) {
                    screen.onActivity(activity -> {
                        String status = ((TextView) activity.findViewById(R.id.preview_status)).getText().toString();
                        assertNotEquals(activity.getString(R.string.preview_failed), status);
                        finished.set(activity.getString(R.string.preview_ready).equals(status)
                            || status.isEmpty()); // Optional ready hint may be hidden.
                    });
                    if (!finished.get()) Thread.sleep(100);
                }
                assertTrue("Polish-only preview must finish", finished.get());
            }
        } finally {
            if (speech != null) speech.shutdown();
            Locale.setDefault(oldLocale);
            SharedPreferences.Editor restore = prefs.edit();
            if (hadSelection) restore.putStringSet(LanguageSettings.PREF_SUPPORTED_LANGUAGES, oldSelection);
            else restore.remove(LanguageSettings.PREF_SUPPORTED_LANGUAGES);
            assertTrue(restore.commit());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }
}
