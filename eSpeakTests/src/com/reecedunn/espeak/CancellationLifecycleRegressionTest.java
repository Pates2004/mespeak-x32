package com.reecedunn.espeak;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CancellationLifecycleRegressionTest {
    private Context storage;

    @Before public void prepare() {
        storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
    }

    private class Service extends TtsService {
        int creations;
        boolean failFirst;
        boolean stopAfterVoice;
        @Override protected int selectLanguageWithFallback(String language, String country, String variant) {
            return onLoadVoice("pl");
        }
        @Override protected SpeechSynthesis createSpeechSynthesis(Context context,
                SpeechSynthesis.SynthReadyCallback callback) {
            final boolean fail = failFirst && ++creations == 1;
            return new SpeechSynthesis(context, callback) {
                @Override public boolean isReady() { return !fail && super.isReady(); }
                @Override public boolean setVoice(Voice voice, VoiceVariant variant) {
                    boolean result = super.setVoice(voice, variant);
                    if (stopAfterVoice) {
                        stopAfterVoice = false;
                        Service.this.onStop();
                    }
                    return result;
                }
            };
        }
        void say(Output output) {
            onSynthesizeText(new SynthesisRequest("Przycisk ustawienia sieci.", new Bundle()), output);
        }
    }

    private Service service(boolean failFirst) throws Exception {
        Service service = new Service();
        service.failFirst = failFirst;
        Field context = TtsService.class.getDeclaredField("storageContext");
        context.setAccessible(true);
        context.set(null, storage);
        Field preferences = TtsService.class.getDeclaredField("mPreferences");
        preferences.setAccessible(true);
        preferences.set(service, android.preference.PreferenceManager.getDefaultSharedPreferences(storage));
        Method initialize = TtsService.class.getDeclaredMethod("initializeTtsEngine");
        initialize.setAccessible(true);
        initialize.invoke(service);
        return service;
    }

    private static class Output implements SynthesisCallback {
        final Service service;
        final int cancelStage;
        volatile boolean stopped;
        int starts, audioCalls, done, errors;
        long accepted;
        Output(Service service, int cancelStage) { this.service = service; this.cancelStage = cancelStage; }
        private void stopConcurrently() {
            stopped = true;
            Thread stop = new Thread(service::onStop, "test-request-stop");
            stop.start();
            try { stop.join(1000); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
            assertFalse("Stop must not wait for the synthesis lock", stop.isAlive());
        }
        @Override public int start(int rate, int format, int channels) {
            starts++;
            if (cancelStage == 1) stopConcurrently();
            return TextToSpeech.SUCCESS; // The successful-start decision preceded stop.
        }
        @Override public int getMaxBufferSize() { return 8192; }
        @Override public int audioAvailable(byte[] data, int offset, int length) {
            audioCalls++;
            if (cancelStage == 2 && audioCalls == 1) stopConcurrently();
            if (stopped) return TextToSpeech.STOPPED;
            accepted += length;
            return TextToSpeech.SUCCESS;
        }
        @Override public int done() { done++; return TextToSpeech.SUCCESS; }
        @Override public void error() { errors++; }
        @Override public void error(int code) { errors++; }
        @Override public boolean hasStarted() { return starts > 0; }
        @Override public boolean hasFinished() { return done > 0; }
        @Override public void rangeStart(int frame, int start, int end) { }
    }

    private void assertNextPhrase(Service service) {
        Output next = new Output(service, 0);
        service.say(next);
        assertEquals(1, next.starts);
        assertTrue(next.accepted > 1000);
        assertEquals(1, next.done);
        assertEquals(0, next.errors);
    }

    @Test public void stopAfterSuccessfulStartDecisionSkipsNativeAudioAndTerminalCallbacks() throws Exception {
        Service service = service(false);
        Output canceled = new Output(service, 1);
        service.say(canceled);
        assertEquals(1, canceled.starts);
        assertEquals(0, canceled.audioCalls);
        assertEquals(0, canceled.done);
        assertEquals(0, canceled.errors);
        assertNextPhrase(service);
    }

    @Test public void stopDuringDeliveryRejectsRemainingAudioAndCompletion() throws Exception {
        Service service = service(false);
        Output canceled = new Output(service, 2);
        service.say(canceled);
        assertEquals(1, canceled.audioCalls);
        assertEquals(0, canceled.accepted);
        assertEquals(0, canceled.done);
        assertEquals(0, canceled.errors);
        assertNextPhrase(service);
    }

    @Test public void stopAfterVoiceSelectionCleansPendingVoiceBeforeNextPhrase() throws Exception {
        Service service = service(false);
        service.stopAfterVoice = true;
        Output canceled = new Output(service, 0);
        service.say(canceled);
        assertEquals(0, canceled.audioCalls);
        assertEquals(0, canceled.done);
        assertEquals(0, canceled.errors);
        assertNextPhrase(service);
    }

    @Test public void failedInitialWrapperRetriesCatalogAndThenSynthesizes() throws Exception {
        Service service = service(true);
        Field catalog = TtsService.class.getDeclaredField("mAllVoices");
        catalog.setAccessible(true);
        assertTrue(((List<?>) catalog.get(service)).isEmpty());
        assertFalse(service.onGetVoices().isEmpty());
        assertEquals(2, service.creations);
        assertNextPhrase(service);
    }

    @Test public void partialCancellationRecoveryMatchesReloadTimingAndAudio() throws Exception {
        final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        final AtomicBoolean stopFirst = new AtomicBoolean();
        final SpeechSynthesis[] holder = new SpeechSynthesis[1];
        final int[] chunks = {0};
        holder[0] = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] data) {
                chunks[0]++;
                audio.write(data, 0, data.length);
                if (stopFirst.compareAndSet(true, false)) holder[0].stop();
            }
            @Override public void onSynthDataComplete() { }
            @Override public void onSynthWordBoundary(int start, int length, int frame) { }
        });
        SpeechSynthesis engine = holder[0];
        Voice polish = null;
        for (Voice voice : engine.getAvailableVoices()) if ("pl".equals(voice.name)) polish = voice;
        assertNotNull(polish);
        for (String variantName : new String[] {"male", "m2"}) {
            for (boolean sonic : new boolean[] {false, true}) {
                VoiceVariant variant = VoiceVariant.parseVoiceVariant(variantName);
                synchronized (CoreState.LOCK) {
                    assertTrue(engine.setVoice(polish, variant));
                    parameters(engine, sonic);
                    stopFirst.set(true);
                    chunks[0] = 0;
                    assertFalse(engine.synthesize("To jest krótki przykład anulowania mowy.", false));
                    assertEquals("No extra native buffer after cancellation", 1, chunks[0]);
                    byte[] recovered = render(engine, polish, variant, sonic, false, audio);
                    byte[] reference = render(engine, polish, variant, sonic, true, audio);
                    byte[] repeated = render(engine, polish, variant, sonic, true, audio);
                    int allowance = Math.max(2206, 2 * Math.abs(reference.length - repeated.length));
                    assertTrue("Recovery must not retain or skip a native clause/tail",
                        Math.abs(recovered.length - reference.length) <= allowance);
                    double ratio = rms(recovered) / rms(reference);
                    assertTrue("Recovery energy must remain comparable to full voice reload", ratio > 0.5 && ratio < 2.0);
                    if ("true".equals(InstrumentationRegistry.getArguments().getString("recordCancellationPcm"))) {
                        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                            .getExternalFilesDir(null), "r43-cancellation-pcm");
                        assertTrue(directory.isDirectory() || directory.mkdirs());
                        String prefix = variantName + "-" + sonic;
                        FileUtils.write(new File(directory, prefix + "-recovered.pcm"), recovered);
                        FileUtils.write(new File(directory, prefix + "-reload.pcm"), reference);
                        FileUtils.write(new File(directory, prefix + "-reload-repeat.pcm"), repeated);
                    }
                }
            }
        }
    }

    private static void parameters(SpeechSynthesis engine, boolean sonic) {
        engine.Rate.setValue(sonic ? 450 : 175);
        engine.setSonicRate(sonic ? 900 : 0);
        engine.Pitch.setValue(50);
        engine.PitchRange.setValue(50);
        engine.Volume.setValue(100);
        engine.Punctuation.setValue(SpeechSynthesis.PUNCT_NONE);
    }

    /** Run one selector per fresh instrumentation process for matched-history PCM. */
    @Test public void compareIndependentCancellationHistory() throws Exception {
        String selected = InstrumentationRegistry.getArguments().getString("cancelCase");
        org.junit.Assume.assumeNotNull(selected);
        int selectedCase = Integer.parseInt(selected);
        assertTrue(selectedCase >= 0 && selectedCase < 4);
        String mode = InstrumentationRegistry.getArguments().getString("cancelMode");
        assertTrue("recovery".equals(mode) || "reload".equals(mode));
        String variantName = selectedCase < 2 ? "male" : "m2";
        boolean sonic = (selectedCase & 1) != 0;
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        AtomicBoolean cancelFirst = new AtomicBoolean(true);
        final SpeechSynthesis[] holder = new SpeechSynthesis[1];
        final int[] prefixChunks = {0};
        holder[0] = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] data) {
                prefixChunks[0]++;
                audio.write(data, 0, data.length);
                if (cancelFirst.compareAndSet(true, false)) {
                    holder[0].stop();
                }
            }
            @Override public void onSynthDataComplete() { }
            @Override public void onSynthWordBoundary(int start, int length, int frame) { }
        });
        SpeechSynthesis engine = holder[0];
        Voice polish = null;
        for (Voice voice : engine.getAvailableVoices()) if ("pl".equals(voice.name)) polish = voice;
        assertNotNull(polish);
        VoiceVariant variant = VoiceVariant.parseVoiceVariant(variantName);
        synchronized (CoreState.LOCK) {
            assertTrue(engine.setVoice(polish, variant));
            parameters(engine, sonic);
            assertFalse(engine.synthesize("Krótka próba anulowania mowy.", false));
            assertEquals(1, prefixChunks[0]);
            byte[] prefix = audio.toByteArray();
            assertTrue(prefix.length > 0);
            byte[] target = render(engine, polish, variant, sonic, "reload".equals(mode), audio);
            File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "r43-independent-cancellation-pcm");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            String name = selectedCase + "-" + mode;
            FileUtils.write(new File(directory, name + "-prefix.pcm"), prefix);
            FileUtils.write(new File(directory, name + "-target.pcm"), target);
        }
    }
    private static byte[] render(SpeechSynthesis engine, Voice voice, VoiceVariant variant,
            boolean sonic, boolean reload, ByteArrayOutputStream audio) {
        if (reload) CoreState.invalidateVoice();
        assertTrue(engine.setVoice(voice, variant));
        parameters(engine, sonic);
        audio.reset();
        assertTrue(engine.synthesize("Ala ma kota. Dobra mowa.", false));
        byte[] result = audio.toByteArray();
        assertTrue(result.length > 1000);
        assertTrue(rms(result) > 1.0);
        return result;
    }
    private static double rms(byte[] pcm) {
        double sum = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            short value = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            sum += (double) value * value;
        }
        return Math.sqrt(sum / Math.max(1, pcm.length / 2));
    }
}
