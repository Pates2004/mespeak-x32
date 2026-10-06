package com.reecedunn.espeak;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.LocaleList;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppearanceSettingsTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    private SharedPreferences preferences() {
        return PreferenceManager.getDefaultSharedPreferences(EspeakApp.getStorageContext());
    }

    private static PreferenceFragment fragment(TtsSettingsActivity activity) {
        return (PreferenceFragment) activity.getFragmentManager().findFragmentById(android.R.id.content);
    }

    private void waitForSettings(ActivityScenario<TtsSettingsActivity> screen,
                                 TtsSettingsActivity previous) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int attempt = 0; attempt < 150 && !ready.get(); attempt++) {
            screen.onActivity(activity -> {
                PreferenceFragment settings = fragment(activity);
                ready.set(activity != previous && settings != null
                        && settings.findPreference("speech_preview") != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("Settings must finish loading after appearance changes", ready.get());
    }

    private void showPreview(ActivityScenario<TtsSettingsActivity> screen) {
        screen.onActivity(activity -> {
            PreferenceFragment settings = fragment(activity);
            ListView list = settings.getView().findViewById(android.R.id.list);
            list.setSelection(settings.getPreferenceScreen().getPreferenceCount() - 1);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void restoreAppearance(String theme, Boolean hints) {
        SharedPreferences.Editor restore = preferences().edit();
        if (theme == null) restore.remove(AppearanceSettings.PREF_THEME);
        else restore.putString(AppearanceSettings.PREF_THEME, theme);
        if (hints == null) restore.remove(AppearanceSettings.PREF_SHOW_HINTS);
        else restore.putBoolean(AppearanceSettings.PREF_SHOW_HINTS, hints);
        assertTrue(restore.commit());
    }

    @Test
    public void themeFollowsSystemUnlessExplicitlyOverridden() {
        assertEquals(R.style.AppThemeLight, AppearanceSettings.themeResource(
                AppearanceSettings.THEME_SYSTEM, Configuration.UI_MODE_NIGHT_NO));
        assertEquals(R.style.AppThemeDark, AppearanceSettings.themeResource(
                AppearanceSettings.THEME_SYSTEM, Configuration.UI_MODE_NIGHT_YES));
        assertEquals(R.style.AppThemeLight, AppearanceSettings.themeResource(
                AppearanceSettings.THEME_LIGHT, Configuration.UI_MODE_NIGHT_YES));
        assertEquals(R.style.AppThemeDark, AppearanceSettings.themeResource(
                AppearanceSettings.THEME_DARK, Configuration.UI_MODE_NIGHT_NO));
        assertEquals(R.style.AppThemeDark, AppearanceSettings.themeResource(
                "invalid", Configuration.UI_MODE_NIGHT_YES));
    }

    @Test
    public void interfaceLanguageUsesOnlyPrimarySystemLanguage() {
        Locale original = Locale.getDefault();
        Configuration system = new Configuration(context.getResources().getConfiguration());
        system.setLocale(new Locale("pl", "PL"));
        assertEquals("pl", AppearanceSettings.interfaceLocale(system).getLanguage());
        Context polish = AppearanceSettings.localizedContext(context, system);
        assertEquals("Motyw", polish.getString(R.string.app_theme_title));
        system.setLocale(Locale.GERMAN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            system.setLocales(new LocaleList(Locale.GERMAN, new Locale("pl", "PL")));
        }
        assertEquals(Locale.ENGLISH, AppearanceSettings.interfaceLocale(system));
        Context english = AppearanceSettings.localizedContext(context, system);
        assertEquals("Theme", english.getString(R.string.app_theme_title));
        system.setLocale(Locale.ENGLISH);
        assertEquals(Locale.ENGLISH, AppearanceSettings.interfaceLocale(system));
        assertEquals(original, Locale.getDefault());
    }

    @Test
    public void themeSelectionPersistsAndPreservesPreview() throws Exception {
        SharedPreferences prefs = preferences();
        String oldTheme = prefs.getString(AppearanceSettings.PREF_THEME, null);
        Boolean oldHints = prefs.contains(AppearanceSettings.PREF_SHOW_HINTS)
                ? prefs.getBoolean(AppearanceSettings.PREF_SHOW_HINTS, true) : null;
        assertTrue(prefs.edit().remove(AppearanceSettings.PREF_THEME).commit());
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            waitForSettings(screen, null);
            showPreview(screen);
            AtomicReference<TtsSettingsActivity> oldActivity = new AtomicReference<>();
            screen.onActivity(activity -> {
                ListPreference theme = (ListPreference) fragment(activity)
                        .findPreference(AppearanceSettings.PREF_THEME);
                assertEquals(activity.getString(R.string.app_theme_title), theme.getTitle());
                assertEquals(AppearanceSettings.THEME_SYSTEM, theme.getValue());
                assertEquals(3, theme.getEntries().length);
                assertEquals(theme.getEntry(), theme.getSummary());
                ((EditText) activity.findViewById(R.id.preview_text)).setText("Preserved preview");
                oldActivity.set(activity);
                assertTrue(theme.getOnPreferenceChangeListener().onPreferenceChange(theme, AppearanceSettings.THEME_DARK));
                theme.setValue(AppearanceSettings.THEME_DARK);
            });
            waitForSettings(screen, oldActivity.get());
            showPreview(screen);
            screen.onActivity(activity -> {
                assertEquals("Preserved preview",
                        ((EditText) activity.findViewById(R.id.preview_text)).getText().toString());
                ListPreference theme = (ListPreference) fragment(activity)
                        .findPreference(AppearanceSettings.PREF_THEME);
                assertEquals(AppearanceSettings.THEME_DARK, theme.getValue());
                assertEquals(activity.getString(R.string.app_theme_dark), theme.getSummary());
                TypedValue background = new TypedValue();
                assertTrue(activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true));
                assertTrue(Color.red(background.data) < 128);
                oldActivity.set(activity);
                assertTrue(theme.getOnPreferenceChangeListener().onPreferenceChange(theme, AppearanceSettings.THEME_LIGHT));
                theme.setValue(AppearanceSettings.THEME_LIGHT);
            });
            waitForSettings(screen, oldActivity.get());
            screen.onActivity(activity -> {
                TypedValue background = new TypedValue();
                assertTrue(activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true));
                assertTrue(Color.red(background.data) > 128);
                assertEquals(AppearanceSettings.THEME_LIGHT,
                        prefs.getString(AppearanceSettings.PREF_THEME, null));
            });
        } finally {
            restoreAppearance(oldTheme, oldHints);
        }
    }

    @Test
    public void optionalHintsHideWithoutHidingLabelsErrorsOrWarnings() throws Exception {
        SharedPreferences prefs = preferences();
        String oldTheme = prefs.getString(AppearanceSettings.PREF_THEME, null);
        Boolean oldHints = prefs.contains(AppearanceSettings.PREF_SHOW_HINTS)
                ? prefs.getBoolean(AppearanceSettings.PREF_SHOW_HINTS, true) : null;
        assertTrue(prefs.edit().putBoolean(AppearanceSettings.PREF_SHOW_HINTS, false).commit());
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            waitForSettings(screen, null);
            showPreview(screen);
            AtomicReference<TtsSettingsActivity> oldActivity = new AtomicReference<>();
            screen.onActivity(activity -> {
                PreferenceFragment settings = fragment(activity);
                CheckBoxPreference hints = (CheckBoxPreference) settings
                        .findPreference(AppearanceSettings.PREF_SHOW_HINTS);
                assertFalse(hints.isChecked());
                assertEquals(activity.getString(R.string.show_usage_hints_title), hints.getTitle());
                assertNull(settings.findPreference(EspeakApp.PREF_SHOW_LAUNCHER).getSummary());
                assertNull(settings.findPreference(VoiceSettings.PREF_IGNORE_SYSTEM_RATE).getSummary());
                assertNotNull(settings.findPreference(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES).getSummary());
                assertEquals(View.GONE, activity.findViewById(R.id.preview_status).getVisibility());
                EditText editor = activity.findViewById(R.id.preview_text);
                assertNotNull(editor.getHint());
                editor.setText("");
                activity.findViewById(R.id.preview_speak).performClick();
                TextView status = activity.findViewById(R.id.preview_status);
                assertEquals(View.VISIBLE, status.getVisibility());
                assertEquals(activity.getString(R.string.preview_empty), status.getText().toString());
                oldActivity.set(activity);
                assertTrue(hints.getOnPreferenceChangeListener().onPreferenceChange(hints, true));
                hints.setChecked(true);
            });
            waitForSettings(screen, oldActivity.get());
            screen.onActivity(activity -> {
                assertTrue(prefs.getBoolean(AppearanceSettings.PREF_SHOW_HINTS, false));
                assertNotNull(fragment(activity).findPreference(EspeakApp.PREF_SHOW_LAUNCHER).getSummary());
            });
        } finally {
            restoreAppearance(oldTheme, oldHints);
        }
    }
}
