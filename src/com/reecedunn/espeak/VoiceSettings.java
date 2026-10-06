/*
 * Copyright (C) 2013 Reece H. Dunn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reecedunn.espeak;

import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class VoiceSettings {
    private final SharedPreferences mPreferences;
    private final SpeechSynthesis mEngine;

    public static final String PREF_DEFAULT_GENDER = "default_gender";
    public static final String PREF_VARIANT = "espeak_variant";
    public static final String PREF_DEFAULT_RATE = "default_rate";
    public static final String PREF_RATE = "espeak_rate";
    public static final String PREF_DEFAULT_PITCH = "default_pitch";
    public static final String PREF_PITCH = "espeak_pitch";
    public static final String PREF_PITCH_RANGE = "espeak_pitch_range";
    public static final String PREF_VOLUME = "espeak_volume";
    public static final String PREF_PUNCTUATION_LEVEL = "espeak_punctuation_level";
    public static final String PREF_PUNCTUATION_CHARACTERS = "espeak_punctuation_characters";
    public static final String PREF_RATE_BOOST = "espeak_rate_boost";
    public static final String PREF_RATE_MODE = "espeak_rate_mode";
    public static final String PREF_IGNORE_SYSTEM_RATE = "espeak_ignore_system_rate";
    public static final int RATE_BOOST_MULTIPLIER = 3;
    public static final String RATE_MODE_SMOOTH = "smooth";
    public static final String RATE_MODE_STANDARD = "standard";
    public static final String RATE_MODE_BOOST = "boost";
    public static final int RATE_MINIMUM = 80;
    public static final int RATE_NORMAL_MAXIMUM = 450;
    public static final int RATE_SMOOTH_MAXIMUM = RATE_NORMAL_MAXIMUM * RATE_BOOST_MULTIPLIER;
    public static final int RATE_SMOOTH_THRESHOLD = 300;

    public static final String PRESET_VARIANT = "variant";
    public static final String PRESET_RATE = "rate";
    public static final String PRESET_PITCH = "pitch";
    public static final String PRESET_PITCH_RANGE = "pitch-range";
    public static final String PRESET_VOLUME = "volume";
    public static final String PRESET_PUNCTUATION_LEVEL = "punctuation-level";
    public static final String PRESET_PUNCTUATION_CHARACTERS = "punctuation-characters";

    public static final String PUNCTUATION_NONE = "none";
    public static final String PUNCTUATION_SOME = "some";
    public static final String PUNCTUATION_ALL = "all";

    public VoiceSettings(SharedPreferences preferences, SpeechSynthesis engine) {
        mPreferences = preferences;
        mEngine = engine;
        migrateRateMode(preferences);
    }

    public VoiceVariant getVoiceVariant() {
        String variant = mPreferences.getString(PREF_VARIANT, null);
        if (variant == null) {
            int gender = getPreferenceValue(PREF_DEFAULT_GENDER, SpeechSynthesis.GENDER_MALE);
            if (gender == SpeechSynthesis.GENDER_FEMALE) {
                return VoiceVariant.parseVoiceVariant(VoiceVariant.FEMALE);
            }
            return VoiceVariant.parseVoiceVariant(VoiceVariant.MALE);
        }
        return VoiceVariant.parseVoiceVariant(variant);
    }

    public int getRate() {
        return getRateDisplayValue(getRateMode(mPreferences),
                getRateSliderValue(mPreferences, mEngine.Rate.getDefaultValue()));
    }

    public int getRateForCaller(int requestedPercent) {
        return getRateForCaller(mPreferences, mEngine.Rate.getDefaultValue(), requestedPercent);
    }

    public static int getRateForCaller(SharedPreferences preferences, int defaultValue,
                                       int requestedPercent) {
        final String mode = getRateMode(preferences);
        final int savedRate = getRateDisplayValue(mode, getRateSliderValue(preferences, defaultValue));
        final int scale = getBooleanPreference(preferences, PREF_IGNORE_SYSTEM_RATE, false)
                || requestedPercent <= 0 ? 100 : requestedPercent;
        final long scaledRate = ((long) savedRate * scale) / 100;
        final int maximum = RATE_MODE_STANDARD.equals(mode) ? RATE_NORMAL_MAXIMUM : RATE_SMOOTH_MAXIMUM;
        return clampRate(scaledRate, RATE_MINIMUM, maximum);
    }

    public static synchronized void migrateRateMode(SharedPreferences preferences) {
        final String currentMode = getStringPreference(preferences, PREF_RATE_MODE, null);
        if (isRateMode(currentMode)) return;
        final boolean legacyRate = preferences.contains(PREF_RATE)
                || preferences.contains(PREF_DEFAULT_RATE) || preferences.contains(PREF_RATE_BOOST);
        final boolean legacyBoost = getBooleanPreference(preferences, PREF_RATE_BOOST, false);
        String mode = legacyBoost ? RATE_MODE_BOOST
                : legacyRate ? RATE_MODE_STANDARD : RATE_MODE_SMOOTH;
        final SharedPreferences.Editor migration = preferences.edit();
        if (legacyBoost) {
            final long legacyPercent = Math.max(0L, Math.min(1000000L,
                    getRatePreferenceValue(preferences, PREF_DEFAULT_RATE, 100)));
            final long base = getRatePreferenceValue(preferences, PREF_RATE, legacyPercent * 175 / 100);
            if (base < RATE_MINIMUM) {
                // Old imported percentages could encode boost below its slider
                // range. Smooth uses identical native timing at those speeds.
                mode = RATE_MODE_SMOOTH;
                final int effective = clampRate(Math.max(0L, base) * RATE_BOOST_MULTIPLIER,
                        RATE_MINIMUM, RATE_SMOOTH_MAXIMUM);
                migration.putString(PREF_RATE, Integer.toString(effective));
            }
        }
        migration.putString(PREF_RATE_MODE, mode)
                .putBoolean(PREF_RATE_BOOST, RATE_MODE_BOOST.equals(mode)).commit();
    }

    public static String getRateMode(SharedPreferences preferences) {
        String mode = getStringPreference(preferences, PREF_RATE_MODE, null);
        if (!isRateMode(mode)) {
            migrateRateMode(preferences);
            mode = getStringPreference(preferences, PREF_RATE_MODE, RATE_MODE_SMOOTH);
        }
        return mode;
    }

    public static boolean setRateMode(SharedPreferences preferences, String mode, int defaultValue) {
        if (!isRateMode(mode)) throw new IllegalArgumentException("Unknown speech-rate mode: " + mode);
        final String previousMode = getRateMode(preferences);
        final int previousRate = getRateSliderValue(preferences, defaultValue);
        final int effectiveRate = getRateDisplayValue(previousMode, previousRate);
        final int storedRate = RATE_MODE_BOOST.equals(mode)
                ? (effectiveRate + RATE_BOOST_MULTIPLIER / 2) / RATE_BOOST_MULTIPLIER : effectiveRate;
        return preferences.edit().putString(PREF_RATE_MODE, mode)
                .putString(PREF_RATE, Integer.toString(clampRate(storedRate,
                        RATE_MINIMUM, getRateSliderMaximum(mode))))
                .putBoolean(PREF_RATE_BOOST, RATE_MODE_BOOST.equals(mode)).commit();
    }

    public static int getRateSliderMaximum(String mode) {
        return RATE_MODE_SMOOTH.equals(mode) ? RATE_SMOOTH_MAXIMUM : RATE_NORMAL_MAXIMUM;
    }

    public static int getRateSliderValue(SharedPreferences preferences, int defaultValue) {
        final String mode = getRateMode(preferences);
        final int safeDefault = clampRate(defaultValue, RATE_MINIMUM, RATE_NORMAL_MAXIMUM);
        final long legacyPercent = Math.max(0L, Math.min(1000000L,
                getRatePreferenceValue(preferences, PREF_DEFAULT_RATE, 100)));
        final long fallback = (legacyPercent * safeDefault) / 100;
        return clampRate(getRatePreferenceValue(preferences, PREF_RATE, fallback),
                RATE_MINIMUM, getRateSliderMaximum(mode));
    }

    public static int getRateDisplayValue(String mode, int sliderValue) {
        final int boundedValue = clampRate(sliderValue, RATE_MINIMUM, getRateSliderMaximum(mode));
        return RATE_MODE_BOOST.equals(mode) ? boundedValue * RATE_BOOST_MULTIPLIER : boundedValue;
    }

    private static int clampRate(long value, int minimum, int maximum) {
        return (int) Math.max(minimum, Math.min((long) maximum, value));
    }

    private static boolean isRateMode(String mode) {
        return RATE_MODE_SMOOTH.equals(mode) || RATE_MODE_STANDARD.equals(mode) || RATE_MODE_BOOST.equals(mode);
    }

    private static long getRatePreferenceValue(SharedPreferences preferences, String key, long fallback) {
        final String value = getStringPreference(preferences, key, null);
        if (value == null) return fallback;
        final String trimmed = value.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException error) {
            if (trimmed.matches("[+-]?[0-9]+")) {
                return trimmed.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
            }
            return fallback;
        }
    }

    private static String getStringPreference(SharedPreferences preferences, String key, String fallback) {
        try {
            return preferences.getString(key, fallback);
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    private static boolean getBooleanPreference(SharedPreferences preferences, String key, boolean fallback) {
        try {
            return preferences.getBoolean(key, fallback);
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    public int getPitch() {
        int min = mEngine.Pitch.getMinValue();
        int max = mEngine.Pitch.getMaxValue();

        int pitch = getPreferenceValue(PREF_PITCH, Integer.MIN_VALUE);
        if (pitch == Integer.MIN_VALUE) {
            pitch = getPreferenceValue(PREF_DEFAULT_PITCH, 100) / 2;
        }

        if (pitch > max) pitch = max;
        if (pitch < min) pitch = min;
        return pitch;
    }

    public int getPitchRange() {
        int min = mEngine.PitchRange.getMinValue();
        int max = mEngine.PitchRange.getMaxValue();

        int range = getPreferenceValue(PREF_PITCH_RANGE, mEngine.PitchRange.getDefaultValue());
        if (range > max) range = max;
        if (range < min) range = min;
        return range;
    }

    public int getVolume() {
        int min = mEngine.Volume.getMinValue();
        int max = mEngine.Volume.getMaxValue();

        int range = getPreferenceValue(PREF_VOLUME, mEngine.Volume.getDefaultValue());
        if (range > max) range = max;
        if (range < min) range = min;
        return range;
    }

    public int getPunctuationLevel() {
        int min = mEngine.Punctuation.getMinValue();
        int max = mEngine.Punctuation.getMaxValue();

        int level = getPreferenceValue(PREF_PUNCTUATION_LEVEL, mEngine.Punctuation.getDefaultValue());
        if (level > max) level = max;
        if (level < min) level = min;
        return level;
    }

    public String getPunctuationCharacters() {
        return mPreferences.getString(PREF_PUNCTUATION_CHARACTERS, null);
    }

    private int getPreferenceValue(String preference, int defaultValue) {
        String prefString = mPreferences.getString(preference, null);
        if (prefString == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(prefString);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject settings = new JSONObject();
        settings.put(PRESET_VARIANT, getVoiceVariant().toString());
        settings.put(PRESET_RATE, getRate());
        settings.put(PRESET_PITCH, getPitch());
        settings.put(PRESET_PITCH_RANGE, getPitchRange());
        settings.put(PRESET_VOLUME, getVolume());
        settings.put(PRESET_PUNCTUATION_CHARACTERS, getPunctuationCharacters());
        switch (getPunctuationLevel()) {
            case SpeechSynthesis.PUNCT_NONE:
                settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_NONE);
                break;
            case SpeechSynthesis.PUNCT_SOME:
                settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_SOME);
                break;
            case SpeechSynthesis.PUNCT_ALL:
                settings.put(PRESET_PUNCTUATION_LEVEL, PUNCTUATION_ALL);
                break;
        }
        return settings;
    }

    public boolean isRateBoostEnabled() {
        return RATE_MODE_BOOST.equals(getRateMode(mPreferences));
    }

    public boolean isSmoothRateEnabled() {
        return RATE_MODE_SMOOTH.equals(getRateMode(mPreferences));
    }

    public boolean isSystemRateIgnored() {
        return getBooleanPreference(mPreferences, PREF_IGNORE_SYSTEM_RATE, false);
    }
}
