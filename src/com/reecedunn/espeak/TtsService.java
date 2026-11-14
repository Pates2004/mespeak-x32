/*
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2012-2015 Reece H. Dunn
 * Copyright (C) 2011 Google Inc.
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

/*
 * This file implements the Android Text-to-Speech engine for eSpeak.
 *
 * Android Version: 4.0 (Ice Cream Sandwich)
 * API Version:     14
 */

package com.reecedunn.espeak;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.util.Log;
import android.util.Pair;

import com.reecedunn.espeak.SpeechSynthesis.SynthReadyCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Implements the eSpeak engine as a {@link TextToSpeechService}.
 *
 * @author msclrhd@gmail.com (Reece H. Dunn)
 * @author alanv@google.com (Alan Viverette)
 */
@SuppressLint("NewApi")
public class TtsService extends TextToSpeechService {
    private static final String TAG = TtsService.class.getSimpleName();
    private static Context storageContext;
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private volatile SpeechSynthesis mEngine;
    private SynthesisCallback mCallback;
    private volatile ActiveRequest mActiveRequest;

    private static final class ActiveRequest {
        final int generation = SpeechSynthesis.getCancellationGeneration();
        volatile boolean canceled;
        boolean nativeStarted;
        boolean outputFailed;
    }

    private static boolean isCanceled(ActiveRequest request) {
        return request.canceled || SpeechSynthesis.isRequestCanceled(request.generation);
    }

    /** Text handed to eSpeak for the current request. */
    private String mSynthText;
    /** Where {@link #mSynthText} starts within the text the caller supplied. */
    private int mSynthTextOffset;
    /** Number of code points in {@link #mSynthText}. */
    private int mSynthTextCodePoints;
    /** Anchor for incremental code point to UTF-16 index conversion. */
    private int mAnchorCodePoint;
    private int mAnchorOffset;

    private List<Voice> mAllVoices = new ArrayList<Voice>();
    private final Map<String, Voice> mAvailableVoices = new HashMap<String, Voice>();
    protected Voice mMatchingVoice = null;

    private SharedPreferences mPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener mOnPreferencesChanged =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (LanguageSettings.PREF_SUPPORTED_LANGUAGES.equals(key)) {
                        rebuildAvailableVoices();
                    }
                }
            };

    @Override
    public void onCreate() {
        storageContext = EspeakApp.getStorageContext();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(storageContext);
        mPreferences.registerOnSharedPreferenceChangeListener(mOnPreferencesChanged);
        CheckVoiceData.ensureVoiceData(storageContext);
        initializeTtsEngine();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mOnPreferencesChanged);
        }
    }

    /**
     * Sets up the native eSpeak engine.
     */
    protected SpeechSynthesis createSpeechSynthesis(Context context, SynthReadyCallback callback) {
        return new SpeechSynthesis(context, callback);
    }

    private synchronized boolean initializeTtsEngine() {
        final SpeechSynthesis candidate = createSpeechSynthesis(storageContext, mSynthCallback);
        List<Voice> voices = candidate.isReady() ? candidate.getAvailableVoices() : new ArrayList<Voice>();
        synchronized (mAvailableVoices) {
            mMatchingVoice = null;
            mAllVoices = new ArrayList<Voice>(voices);
            if (DEBUG) Log.i(TAG, "initializeTtsEngine(): loaded voices=" + mAllVoices.size());
        }
        rebuildAvailableVoices();
        // Publish readiness only after its corresponding catalog is complete.
        mEngine = candidate;
        return candidate.isReady();
    }

    private boolean ensureEngineReady() {
        final SpeechSynthesis current = mEngine;
        if (current != null && current.isReady()) return true;
        synchronized (this) {
            if (mEngine != null && mEngine.isReady()) return true;
            if (!CheckVoiceData.ensureVoiceData(storageContext)) return false;
            return initializeTtsEngine();
        }
    }

    @Override
    protected String[] onGetLanguage() {
        // This is used to specify the language requested from GetSampleText.
        final Voice voice;
        synchronized (mAvailableVoices) {
            voice = mMatchingVoice;
        }
        if (voice == null) {
            return new String[] { "eng", "GBR", "" };
        }
        return new String[] {
            voice.locale.getISO3Language(),
            voice.locale.getISO3Country(),
            voice.locale.getVariant()
        };
    }

    private Pair<Voice, Integer> findVoice(String language, String country, String variant) {
        if (!ensureEngineReady()) return new Pair<>(null, TextToSpeech.LANG_MISSING_DATA);
        if (!CheckVoiceData.hasBaseResources(storageContext)) {
            return new Pair<>(null, TextToSpeech.LANG_MISSING_DATA);
        }

        final Locale query = new Locale(language, country, variant);

        Voice languageVoice = null;
        Voice countryVoice = null;

        synchronized (mAvailableVoices) {
            for (Voice voice : mAvailableVoices.values()) {
                switch (voice.match(query)) {
                    case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
                        return new Pair<>(voice, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
                    case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                        countryVoice = voice;
                    case TextToSpeech.LANG_AVAILABLE:
                        languageVoice = voice;
                        break;
                }
            }
        }

        if (languageVoice == null) {
            return new Pair<>(null, TextToSpeech.LANG_NOT_SUPPORTED);
        } else if (countryVoice == null) {
            return new Pair<>(languageVoice, TextToSpeech.LANG_AVAILABLE);
        } else {
            return new Pair<>(countryVoice, TextToSpeech.LANG_COUNTRY_AVAILABLE);
        }
    }

    private Pair<Voice, Integer> getDefaultVoiceFor(String language, String country, String variant) {
        final Pair<Voice, Integer> match = findVoice(language, country, variant);
        if (match.second == TextToSpeech.LANG_NOT_SUPPORTED) {
            synchronized (mAvailableVoices) {
                if (mMatchingVoice != null && mAvailableVoices.containsKey(mMatchingVoice.name)) {
                    return new Pair<>(mMatchingVoice, TextToSpeech.LANG_AVAILABLE);
                }
                // Keep availability, default name and preview consistent when
                // the requested system language has been disabled.
                Voice fallback = null;
                for (Voice available : mAvailableVoices.values()) {
                    if (fallback == null || available.name.compareTo(fallback.name) < 0) fallback = available;
                }
                if (fallback != null) return new Pair<>(fallback, TextToSpeech.LANG_AVAILABLE);
            }
        }
        switch (match.second) {
            case TextToSpeech.LANG_AVAILABLE:
                if (language.equals("fr") || language.equals("fra")) {
                    return new Pair<>(findVoice(language, "FRA", "").first, match.second);
                }
                if (language.equals("pt") || language.equals("por")) {
                    return new Pair<>(findVoice(language, "PRT", "").first, match.second);
                }
                return new Pair<>(findVoice(language, "", "").first, match.second);
            case TextToSpeech.LANG_COUNTRY_AVAILABLE:
                if ((language.equals("vi") || language.equals("vie")) && (country.equals("VN") || country.equals("VNM"))) {
                    return new Pair<>(findVoice(language, country, "hue").first, match.second);
                }
                return new Pair<>(findVoice(language, country, "").first, match.second);
            default:
                return match;
        }
    }

    @Override
    protected int onIsLanguageAvailable(String language, String country, String variant) {
        return getDefaultVoiceFor(language, country, variant).second;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        final Pair<Voice, Integer> match = getDefaultVoiceFor(language, country, variant);
        if (match.first != null) {
            synchronized (mAvailableVoices) {
                mMatchingVoice = match.first;
            }
            return match.second;
        }
        return match.second;
    }

    @Override
    protected Set<String> onGetFeaturesForLanguage(String lang, String country, String variant) {
        return new HashSet<String>();
    }

    @Override
    public String onGetDefaultVoiceNameFor(String language, String country, String variant) {
        final Voice match = getDefaultVoiceFor(language, country, variant).first;
        return (match == null) ? null : match.name;
    }

    @Override
    public List<android.speech.tts.Voice> onGetVoices() {
        ensureEngineReady();
        rebuildAvailableVoices();
        List<android.speech.tts.Voice> voices = new ArrayList<android.speech.tts.Voice>();
        synchronized (mAvailableVoices) {
            for (Voice voice : mAvailableVoices.values()) {
                int quality = android.speech.tts.Voice.QUALITY_NORMAL;
                int latency = android.speech.tts.Voice.LATENCY_VERY_LOW;
                Locale locale = new Locale(voice.locale.getISO3Language(), voice.locale.getISO3Country(), voice.locale.getVariant());
                Set<String> features = onGetFeaturesForLanguage(locale.getLanguage(), locale.getCountry(), locale.getVariant());
                voices.add(new android.speech.tts.Voice(voice.name, voice.locale, quality, latency, false, features));
            }
        }
        return voices;
    }

    @Override
    public int onIsValidVoiceName(String name) {
        if (!ensureEngineReady()) return TextToSpeech.ERROR;
        synchronized (mAvailableVoices) {
            Voice voice = mAvailableVoices.get(name);
            return (voice == null) ? TextToSpeech.ERROR : TextToSpeech.SUCCESS;
        }
    }

    @Override
    public int onLoadVoice(String name) {
        if (!ensureEngineReady()) return TextToSpeech.ERROR;
        synchronized (mAvailableVoices) {
            Voice voice = mAvailableVoices.get(name);
            if (voice == null) {
                return TextToSpeech.ERROR;
            }
            mMatchingVoice = voice;
            return TextToSpeech.SUCCESS;
        }
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.i(TAG, "Received stop request.");

        final ActiveRequest active = mActiveRequest;
        if (active != null) active.canceled = true;
        final SpeechSynthesis engine = mEngine;
        if (engine != null) engine.stop();
    }

    static String getRequestString(SynthesisRequest request) {
        final CharSequence text = request.getCharSequenceText();
        return text == null ? null : text.toString();
    }

    protected int selectLanguageWithFallback(String language, String country, String variant) {
        final int result = onLoadLanguage(language, country, variant);
        switch (result) {
            case TextToSpeech.LANG_MISSING_DATA:
                return TextToSpeech.ERROR;
            case TextToSpeech.LANG_NOT_SUPPORTED:
                // fall back to a previously selected or available voice instead of failing.
                // This allows screen readers that request the system language (e.g. Jieshuo)
                // to still work when the user has selected only other languages.
                synchronized (mAvailableVoices) {
                    // Prefer reusing the last matching voice if one is already selected.
                    if (mMatchingVoice != null) {
                        return TextToSpeech.SUCCESS;
                    }
                    // Otherwise, pick an arbitrary available voice if any exist.
                    if (!mAvailableVoices.isEmpty()) {
                        mMatchingVoice = mAvailableVoices.values().iterator().next();
                        return TextToSpeech.SUCCESS;
                    }
                }
                // No previous voice and no available voices to fall back to.
                return TextToSpeech.ERROR;
        }
        return TextToSpeech.SUCCESS;
    }

    private int selectVoice(SynthesisRequest request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final String name = request.getVoiceName();
            if (name != null && !name.isEmpty()
                    && onLoadVoice(name) == TextToSpeech.SUCCESS) {
                return TextToSpeech.SUCCESS;
            }
            // Deliberately fall through when the named voice is unknown rather
            // than returning its error. The framework attaches a voice name to
            // every request on API 21+ -- including the system default one when
            // the client never picked a voice itself -- so a name that has been
            // filtered out of the user's language selection would otherwise
            // fail every request and make selectLanguageWithFallback() below
            // unreachable. The name is a hint; the language cascade decides.
        }
        return selectLanguageWithFallback(request.getLanguage(), request.getCountry(), request.getVariant());
    }

    /**
     * Reports a synthesis failure to the caller.
     *
     * <p>The framework only dispatches an error once {@code done()} follows, and
     * treats a request that returns without calling either as a successful empty
     * utterance, which hides failures from screen readers.
     */
    private void reportError(SynthesisCallback callback, int errorCode) {
        final ActiveRequest active = mActiveRequest;
        if (active != null && (active.canceled
                || (!active.outputFailed && SpeechSynthesis.isRequestCanceled(active.generation)))) return;
        // error(int) has been available since API 21, which is minSdk here, so
        // the code always reaches the caller.
        callback.error(errorCode);
        callback.done();
    }

    /**
     * Converts a 0-based code point index within {@link #mSynthText} into the
     * UTF-16 index that {@link SynthesisCallback#rangeStart} expects.
     *
     * <p>Walks forward from the previous result, since word events arrive in text
     * order; the occasional out-of-order event falls back to a full rescan.
     */
    private int codePointToOffset(int codePointIndex) {
        if (codePointIndex <= 0) {
            return 0;
        }
        if (codePointIndex >= mSynthTextCodePoints) {
            return mSynthText.length();
        }
        if (codePointIndex < mAnchorCodePoint) {
            mAnchorCodePoint = 0;
            mAnchorOffset = 0;
        }
        mAnchorOffset = mSynthText.offsetByCodePoints(
                mAnchorOffset, codePointIndex - mAnchorCodePoint);
        mAnchorCodePoint = codePointIndex;
        return mAnchorOffset;
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        final ActiveRequest active = new ActiveRequest();
        mActiveRequest = active;
        try {
            if (!ensureEngineReady()) {
                if (!isCanceled(active)) reportError(callback, TextToSpeech.ERROR_NOT_INSTALLED_YET);
                return;
            }
            if (!isCanceled(active)) synthesizeRequest(request, callback, active);
        } finally {
            mActiveRequest = null;
        }
    }

    private void synthesizeRequest(SynthesisRequest request, SynthesisCallback callback, ActiveRequest active) {
        if (selectVoice(request) == TextToSpeech.ERROR) {
            reportError(callback, CheckVoiceData.hasBaseResources(storageContext)
                    ? TextToSpeech.ERROR_SERVICE : TextToSpeech.ERROR_NOT_INSTALLED_YET);
            return;
        }

        final Voice voice;
        synchronized (mAvailableVoices) {
            voice = mMatchingVoice;
        }
        if (voice == null) {
            reportError(callback, TextToSpeech.ERROR_SERVICE);
            return;
        }

        String text = getRequestString(request);
        if (text == null) {
            reportError(callback, TextToSpeech.ERROR_INVALID_REQUEST);
            return;
        }

        if (DEBUG) {
            Log.i(TAG, "Received synthesis request: {language=\"" + voice.name + "\"}");

            final Bundle params = request.getParams();
            for (String key : params.keySet()) {
                Log.v(TAG,
                        "Synthesis request contained param {" + key + ", " + params.get(key) + "}");
            }
        }

        int textOffset = 0;
        if (text.startsWith("<?xml"))
        {
            // eSpeak does not recognise/skip "<?...?>" preprocessing tags,
            // so need to remove these before passing to synthesize. A
            // declaration missing its "?>" is left alone rather than having its
            // first character eaten by a -1 index.
            final int terminator = text.indexOf("?>");
            if (terminator >= 0)
            {
                final int declarationEnd = terminator + 2;
                // Track what was dropped from the front, so that word boundaries can
                // be reported against the text the caller actually passed in. This
                // mirrors what String.trim() strips (anything <= ' ').
                textOffset = declarationEnd;
                while (textOffset < text.length() && text.charAt(textOffset) <= ' ') {
                    textOffset++;
                }
                text = text.substring(declarationEnd).trim();
            }
        }

        synchronized (CoreState.LOCK) {
            try {
                if (!isCanceled(active)) synthesizeSelectedVoice(request, callback, voice, text, textOffset, active);
            } finally {
                if (!active.nativeStarted && isCanceled(active)) mEngine.resetCanceledSynthesis(active.generation);
            }
        }
    }

    private void synthesizeSelectedVoice(SynthesisRequest request, SynthesisCallback callback,
            Voice voice, String text, int textOffset, ActiveRequest active) {
        if (callback.start(mEngine.getSampleRate(), mEngine.getAudioFormat(),
                mEngine.getChannelCount()) != TextToSpeech.SUCCESS) {
            // The framework has rejected/canceled this request. Do not load a
            // voice, run synthesis, or emit a contradictory completion event.
            return;
        }
        mSynthText = text;
        mSynthTextOffset = textOffset;
        mSynthTextCodePoints = text.codePointCount(0, text.length());
        mAnchorCodePoint = 0;
        mAnchorOffset = 0;

        mCallback = callback;
        try {
            if (!isCanceled(active)) configureAndSynthesize(request, callback, voice, text, active);
        } finally {
            mCallback = null;
            mSynthText = null;
            mSynthTextOffset = 0;
            mSynthTextCodePoints = 0;
            mAnchorCodePoint = 0;
            mAnchorOffset = 0;
        }
    }

    private void configureAndSynthesize(SynthesisRequest request, SynthesisCallback callback,
            Voice voice, String text, ActiveRequest active) {
        final VoiceSettings settings = new VoiceSettings(PreferenceManager.getDefaultSharedPreferences(storageContext), mEngine);
        if (isCanceled(active)) return;
        if (!mEngine.setVoice(voice, settings.getVoiceVariant())) {
            if (!isCanceled(active)) reportError(callback, TextToSpeech.ERROR_SYNTHESIS);
            return;
        }
        if (isCanceled(active)) return;

        final int rate = settings.getRateForCaller(request.getSpeechRate());
        final int normalMaximum = mEngine.Rate.getMaxValue();
        if (settings.isSmoothRateEnabled()) {
            mEngine.Rate.setValue(Math.min(rate, VoiceSettings.RATE_SMOOTH_THRESHOLD));
            mEngine.setSmoothRate(rate);
        } else {
            mEngine.Rate.setValue(Math.min(rate, normalMaximum));
            mEngine.setSonicRate(settings.isRateBoostEnabled() && rate > normalMaximum ? rate : 0);
        }
        mEngine.Pitch.setValue(settings.getPitch(), request.getPitch());
        mEngine.PitchRange.setValue(settings.getPitchRange());
        mEngine.Volume.setValue(settings.getVolume());
        mEngine.Punctuation.setValue(settings.getPunctuationLevel());
        mEngine.setPunctuationCharacters(settings.getPunctuationCharacters());
        if (isCanceled(active)) return;
        active.nativeStarted = true;
        final int result = mEngine.synthesize(text, text.startsWith("<speak"), active.generation);
        if (active.outputFailed && !active.canceled) {
            reportError(callback, TextToSpeech.ERROR_OUTPUT);
        } else if (result == SpeechSynthesis.SYNTHESIS_ERROR && !isCanceled(active)) {
            reportError(callback, TextToSpeech.ERROR_SYNTHESIS);
        }
    }

    protected void rebuildAvailableVoices() {
        synchronized (mAvailableVoices) {
            mAvailableVoices.clear();
            List<Voice> voices = mAllVoices;
            if (mPreferences != null) {
                voices = LanguageSettings.filterVoices(mAllVoices, mPreferences);
            }
            for (Voice voice : voices) {
                mAvailableVoices.put(voice.name, voice);
            }
            if (DEBUG) {
                Set<String> selected = LanguageSettings.getSelectedLanguages(mPreferences);
                Log.i(TAG, "Rebuilt voices: selected=" + (selected == null ? "ALL" : selected.size()) +
                        ", exposed=" + mAvailableVoices.size());
            }
            if (mMatchingVoice != null && !mAvailableVoices.containsKey(mMatchingVoice.name)) {
                mMatchingVoice = null;
            }
        }
    }

    /**
     * Pipes synthesizer output from native eSpeak to an {@link AudioTrack}.
     */
    private final SpeechSynthesis.SynthReadyCallback mSynthCallback = new SynthReadyCallback() {
        @Override
        public void onSynthDataReady(byte[] audioData) {
            final ActiveRequest active = mActiveRequest;
            if (active == null || isCanceled(active) || mCallback == null) return;
            if ((audioData == null) || (audioData.length == 0)) {
                onSynthDataComplete();
                return;
            }

            final int maxBytesToCopy = mCallback.getMaxBufferSize();
            if (maxBytesToCopy <= 0) {
                // Reject a broken output buffer instead of looping with zero
                // progress. This path must also invalidate the reusable voice.
                active.outputFailed = true;
                mEngine.stop();
                return;
            }

            int offset = 0;

            while (offset < audioData.length) {
                if (isCanceled(active)) return;
                final int bytesToWrite = Math.min(maxBytesToCopy, (audioData.length - offset));
                final int result = mCallback.audioAvailable(audioData, offset, bytesToWrite);
                if (result != TextToSpeech.SUCCESS) {
                    // The framework has stopped accepting audio for this
                    // request, so the rest of the buffer has nowhere to go.
                    // A stop normally reaches the engine through onStop();
                    // stopping here as well covers a failure that arrives
                    // without one.
                    if (result != TextToSpeech.STOPPED && !isCanceled(active)) active.outputFailed = true;
                    mEngine.stop();
                    return;
                }
                offset += bytesToWrite;
            }
        }

        @Override
        public void onSynthDataComplete() {
            final ActiveRequest active = mActiveRequest;
            if (active != null && !active.outputFailed && !isCanceled(active) && mCallback != null) mCallback.done();
        }

        @Override
        public void onSynthWordBoundary(int textPosition, int textLength, int markerInFrames) {
            final ActiveRequest active = mActiveRequest;
            if (active == null || isCanceled(active) || mCallback == null) return;
            // rangeStart() is API 26; below that the framework has no way to
            // deliver word boundaries to the caller.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || mSynthText == null) {
                return;
            }

            // eSpeak counts code points from 1, rangeStart() wants 0-based UTF-16
            // indices into the text the caller supplied.
            final int wordStart = textPosition - 1;
            final int start = codePointToOffset(wordStart);
            final int end = codePointToOffset(wordStart + Math.max(textLength, 0));
            if (end <= start) {
                return;
            }

            mCallback.rangeStart(markerInFrames, mSynthTextOffset + start, mSynthTextOffset + end);
        }
    };
}
