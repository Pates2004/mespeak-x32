package com.reecedunn.espeak.preference;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.reecedunn.espeak.R;

import java.util.Locale;

/** Inline, keyboard-accessible preview using this application's TTS service. */
public final class SpeechPreviewPreference extends Preference {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;
    private int session;
    private int utterance;
    private String activeUtterance;
    private String text;
    private String pendingText;
    private int status = R.string.preview_ready;
    private TextView statusView;

    public SpeechPreviewPreference(Context context, String restoredText) {
        super(context);
        setKey("speech_preview");
        setLayoutResource(R.layout.speech_preview);
        setPersistent(false);
        setSelectable(false);
        text = restoredText == null ? context.getString(R.string.preview_sample) : restoredText;
    }

    public String getText() { return text; }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        EditText editor = view.findViewById(R.id.preview_text);
        TextWatcher oldWatcher = (TextWatcher) editor.getTag();
        if (oldWatcher != null) editor.removeTextChangedListener(oldWatcher);
        if (!editor.getText().toString().equals(text)) editor.setText(text);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                text = s.toString();
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        editor.addTextChangedListener(watcher);
        editor.setTag(watcher);
        statusView = view.findViewById(R.id.preview_status);
        statusView.setText(status);
        view.findViewById(R.id.preview_speak).setOnClickListener(v -> speak());
        view.findViewById(R.id.preview_stop).setOnClickListener(v -> shutdown());
    }

    private void setStatus(int message) {
        status = message;
        if (statusView != null) statusView.setText(message);
    }

    private void speak() {
        String input = text.trim();
        if (input.isEmpty()) {
            setStatus(R.string.preview_empty);
            return;
        }
        if (input.length() > TextToSpeech.getMaxSpeechInputLength()) {
            setStatus(R.string.preview_too_long);
            return;
        }
        pendingText = input;
        if (ready) {
            speakPending();
            return;
        }
        if (tts != null) return;
        setStatus(R.string.preview_starting);
        final int requestSession = ++session;
        try {
            // The framework normally falls back to another engine if binding
            // fails. A mespeak preview must never silently test that engine.
            Context engineContext = new ContextWrapper(getContext().getApplicationContext()) {
                @Override
                public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
                    String target = intent.getComponent() == null ? intent.getPackage()
                            : intent.getComponent().getPackageName();
                    return getPackageName().equals(target)
                            && super.bindService(intent, connection, flags);
                }
            };
            tts = new TextToSpeech(engineContext, result -> handler.post(() -> {
                if (requestSession != session) return;
                if (result != TextToSpeech.SUCCESS || tts == null) {
                    fail();
                    return;
                }
                ready = true;
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { announce(id, R.string.preview_speaking); }
                    @Override public void onDone(String id) { announce(id, R.string.preview_ready); }
                    @Override public void onError(String id) { announce(id, R.string.preview_failed); }
                    private void announce(String id, int message) {
                        handler.post(() -> {
                            if (session == requestSession && id.equals(activeUtterance)) setStatus(message);
                        });
                    }
                });
                speakPending();
            }), getContext().getPackageName());
            handler.postDelayed(() -> {
                if (requestSession == session && !ready) fail();
            }, 15000);
        } catch (RuntimeException e) {
            fail();
        }
    }

    private void speakPending() {
        if (pendingText == null || tts == null) return;
        try {
            int language = tts.setLanguage(Locale.getDefault());
            if (language < TextToSpeech.LANG_AVAILABLE) language = tts.setLanguage(Locale.ENGLISH);
            if (language < TextToSpeech.LANG_AVAILABLE) {
                fail();
                return;
            }
            // Neutral Android multipliers: VoiceSettings in TtsService applies
            // the saved speed, Sonic boost, pitch, modulation and variant.
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.0f);
            activeUtterance = "mespeak-preview-" + session + "-" + (++utterance);
            int result = tts.speak(pendingText, TextToSpeech.QUEUE_FLUSH, new Bundle(), activeUtterance);
            pendingText = null;
            if (result == TextToSpeech.ERROR) fail();
        } catch (RuntimeException e) {
            fail();
        }
    }

    private void fail() {
        shutdown();
        setStatus(R.string.preview_failed);
    }

    /** Called when leaving settings; only this client's utterances are stopped. */
    public void shutdown() {
        ++session;
        handler.removeCallbacksAndMessages(null);
        ready = false;
        pendingText = null;
        activeUtterance = null;
        TextToSpeech old = tts;
        tts = null;
        if (old != null) {
            try { old.stop(); } catch (RuntimeException e) { /* Engine disconnected. */ }
            try { old.shutdown(); } catch (RuntimeException e) { /* Already disconnected. */ }
        }
        setStatus(R.string.preview_ready);
    }
}
