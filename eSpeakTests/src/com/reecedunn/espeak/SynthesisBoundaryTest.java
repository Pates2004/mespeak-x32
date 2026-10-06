package com.reecedunn.espeak;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SynthesisBoundaryTest {
    @Test
    public void rejectedNullTextDoesNotCompleteAndOrdinaryTextCompletesOnce() {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        AtomicInteger completions = new AtomicInteger();
        AtomicInteger audioBytes = new AtomicInteger();
        SpeechSynthesis engine = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] audioData) {
                audioBytes.addAndGet(audioData.length);
            }
            @Override public void onSynthDataComplete() {
                completions.incrementAndGet();
            }
            @Override public void onSynthWordBoundary(int textPosition, int textLength, int markerInFrames) { }
        });
        assertTrue(engine.getSampleRate() > 0);
        assertFalse(engine.synthesize(null, false));
        assertEquals(0, completions.get());
        assertEquals(0, audioBytes.get());
        assertTrue(engine.synthesize("This is an ordinary short synthesis test.", false));
        assertEquals(1, completions.get());
        assertTrue(audioBytes.get() > 0);
        int previousBytes = audioBytes.get();
        assertFalse(engine.synthesize(null, false));
        assertEquals(1, completions.get());
        assertEquals(previousBytes, audioBytes.get());
    }

    @Test
    public void scaledPitchClampsBeforeIntegerNarrowing() {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        SpeechSynthesis engine = new SpeechSynthesis(storage, null);
        int previousPitch = engine.Pitch.getValue();
        try {
            engine.Pitch.setValue(50, 100);
            assertEquals(50, engine.Pitch.getValue());
            engine.Pitch.setValue(50, 200);
            // The classic engine caps its pitch parameter at 99.
            assertEquals(99, engine.Pitch.getValue());
            engine.Pitch.setValue(50, Integer.MAX_VALUE);
            assertEquals(99, engine.Pitch.getValue());
            engine.Pitch.setValue(50, Integer.MIN_VALUE);
            assertEquals(0, engine.Pitch.getValue());
        } finally {
            engine.Pitch.setValue(previousPitch);
        }
    }
}
