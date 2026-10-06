package com.reecedunn.espeak;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import java.util.Locale;

public final class AppearanceSettings {
    public static final String PREF_THEME = "app_theme";
    public static final String PREF_SHOW_HINTS = "show_usage_hints";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private AppearanceSettings() { }

    private static SharedPreferences preferences() {
        return PreferenceManager.getDefaultSharedPreferences(EspeakApp.getStorageContext());
    }

    static Locale interfaceLocale(Configuration systemConfiguration) {
        Locale primary = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? systemConfiguration.getLocales().get(0) : systemConfiguration.locale;
        return primary != null && "pl".equals(primary.getLanguage())
                ? new Locale("pl") : Locale.ENGLISH;
    }

    public static Context localizedContext(Context context) {
        return localizedContext(context, Resources.getSystem().getConfiguration());
    }

    static Context localizedContext(Context context, Configuration systemConfiguration) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        Locale locale = interfaceLocale(systemConfiguration);
        configuration.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        }
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    private static String themeValue(String value) {
        return THEME_LIGHT.equals(value) || THEME_DARK.equals(value) ? value : THEME_SYSTEM;
    }

    static int themeResource(String value, int uiMode) {
        String selected = themeValue(value);
        boolean dark = THEME_DARK.equals(selected) || THEME_SYSTEM.equals(selected)
                && (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return dark ? R.style.AppThemeDark : R.style.AppThemeLight;
    }

    public static void applyTheme(Activity activity) {
        activity.setTheme(themeResource(preferences().getString(PREF_THEME, THEME_SYSTEM),
                activity.getResources().getConfiguration().uiMode));
    }

    public static boolean areHintsEnabled() {
        return preferences().getBoolean(PREF_SHOW_HINTS, true);
    }

    public static void setOptionalSummary(Preference preference, int resource) {
        preference.setSummary(areHintsEnabled() ? preference.getContext().getString(resource) : null);
    }

    public static void addPreferences(Activity activity, PreferenceGroup group) {
        ListPreference theme = new ListPreference(activity);
        theme.setKey(PREF_THEME);
        theme.setTitle(R.string.app_theme_title);
        theme.setDialogTitle(R.string.app_theme_title);
        theme.setEntries(new CharSequence[] {
                activity.getString(R.string.app_theme_system),
                activity.getString(R.string.app_theme_light),
                activity.getString(R.string.app_theme_dark) });
        theme.setEntryValues(new CharSequence[] { THEME_SYSTEM, THEME_LIGHT, THEME_DARK });
        theme.setDefaultValue(THEME_SYSTEM);
        theme.setValue(themeValue(preferences().getString(PREF_THEME, THEME_SYSTEM)));
        theme.setSummary(theme.getEntry());
        theme.setOnPreferenceChangeListener((preference, value) -> {
            if (!value.equals(theme.getValue())) recreateAfterPreferenceChange(activity);
            return true;
        });
        group.addPreference(theme);

        CheckBoxPreference hints = new CheckBoxPreference(activity);
        hints.setKey(PREF_SHOW_HINTS);
        hints.setTitle(R.string.show_usage_hints_title);
        hints.setDefaultValue(true);
        hints.setChecked(areHintsEnabled());
        hints.setOnPreferenceChangeListener((preference, value) -> {
            if (!value.equals(hints.isChecked())) recreateAfterPreferenceChange(activity);
            return true;
        });
        group.addPreference(hints);
    }

    private static void recreateAfterPreferenceChange(Activity activity) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) activity.recreate();
        });
    }
}
