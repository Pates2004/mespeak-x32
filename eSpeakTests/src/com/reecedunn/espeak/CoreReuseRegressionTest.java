package com.reecedunn.espeak;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CoreReuseRegressionTest {
    private Context storage;
    private SpeechSynthesis engine;
    private Voice polish;
    private Voice english;
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();
    private final List<String> boundaries = new ArrayList<>();

    @Before
    public void prepare() {
        storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        engine = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] data) { audio.write(data, 0, data.length); }
            @Override public void onSynthDataComplete() { }
            @Override public void onSynthWordBoundary(int position, int length, int frame) {
                boundaries.add(position + ":" + length);
            }
        });
        for (Voice voice : engine.getAvailableVoices()) {
            if (voice.name.equals("pl")) polish = voice;
            if (voice.name.equals("en")) english = voice;
        }
        assertNotNull(polish);
        assertNotNull(english);
        CoreState.invalidateVoice();
    }

    private void parameters(int pitch, int modulation, boolean accelerated) {
        engine.Rate.setValue(accelerated ? 300 : 175);
        engine.setSmoothRate(accelerated ? 550 : 175);
        engine.Pitch.setValue(pitch);
        engine.PitchRange.setValue(modulation);
        engine.Volume.setValue(100);
        engine.Punctuation.setValue(SpeechSynthesis.PUNCT_NONE);
        engine.setPunctuationCharacters("");
    }

    private byte[] render(Voice voice, VoiceVariant variant, String text, boolean force,
            int pitch, int modulation, int rateMode) {
        synchronized (CoreState.LOCK) {
            if (force) CoreState.invalidateVoice();
            assertTrue(engine.setVoice(voice, variant));
            parameters(pitch, modulation, rateMode == 1);
            if (rateMode == 2) {
                engine.Rate.setValue(450);
                engine.setSonicRate(900);
            }
            audio.reset();
            boundaries.clear();
            assertTrue(engine.synthesize(text, false));
            assertTrue(audio.size() > 1000);
            return audio.toByteArray();
        }
    }

    @Test
    public void sameVoiceReusesSharedSelectionAndChangesStillReload() {
        VoiceVariant male = VoiceVariant.parseVoiceVariant("male");
        assertTrue(engine.setVoice(polish, male));
        String selection = CoreState.selectedVoice;
        SpeechSynthesis second = new SpeechSynthesis(storage, null);
        assertTrue(second.setVoice(polish, male));
        assertSame("A hit must retain the successful selection", selection, CoreState.selectedVoice);
        assertTrue(second.setVoice(english, male));
        assertNotSame(selection, CoreState.selectedVoice);
        assertTrue(second.setVoice(polish, VoiceVariant.parseVoiceVariant("female-young")));
        assertTrue(CoreState.selectedVoice.endsWith(":2:12"));
        assertTrue(engine.setVoice(polish, VoiceVariant.parseVoiceVariant("f3")));
        assertTrue(CoreState.selectedVoice.startsWith("name:"));
        Voice invalid = new Voice("missing", "missing", 0, 0, Locale.ENGLISH);
        assertFalse(engine.setVoice(invalid, VoiceVariant.parseVoiceVariant("f3")));
        assertNull(CoreState.selectedVoice);
        assertTrue(engine.setVoice(polish, male));
    }

    @Test
    public void cachedAndReloadedOrdinarySpeechHaveMatchingTiming() throws Exception {
        String[] texts = {"Pierwszy dzień. Sześćset osób.", "Ala ma kota, a kot ma miskę.",
                "Today is a good day. One, two, three."};
        String[] variants = {"male", "f3", "m2"};
        File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "r38-audio-comparison");
        assertTrue(output.isDirectory() || output.mkdirs());
        for (int i = 0; i < texts.length; i++) {
            Voice voice = i == 2 ? english : polish;
            VoiceVariant variant = VoiceVariant.parseVoiceVariant(variants[i]);
            for (int setting = 0; setting < 3; setting++) {
                int pitch = setting == 0 ? 50 : 63;
                int modulation = setting == 0 ? 50 : 72;
                byte[] baseline = render(voice, variant, texts[i], true, pitch, modulation, setting);
                byte[] secondBaseline = render(voice, variant, texts[i], true, pitch, modulation, setting);
                List<String> expectedWords = new ArrayList<>(boundaries);
                String selection = CoreState.selectedVoice;
                byte[] cached = render(voice, variant, texts[i], false, pitch, modulation, setting);
                assertSame(selection, CoreState.selectedVoice);
                assertEquals(expectedWords, boundaries);
                // Waveforms contain native oscillator/noise history. The existing
                // forced-reload path is sampled twice as the variability control.
                String name = i + "-" + setting;
                FileUtils.write(new File(output, name + "-reload.pcm"), baseline);
                FileUtils.write(new File(output, name + "-reload-repeat.pcm"), secondBaseline);
                FileUtils.write(new File(output, name + "-cached.pcm"), cached);
                Log.i("CoreReuseRegression", name + " PCM bytes=" + baseline.length
                        + ", repeated=" + secondBaseline.length + ", cached=" + cached.length
                        + ", rate=22050, channels=1, bits=16");
                // Even two forced reloads differ due to oscillator/noise history.
                // Compare against that control plus a 50 ms rounding allowance,
                // rather than pretending ordinary eSpeak PCM is deterministic.
                int tolerance = Math.max(2206, Math.abs(baseline.length - secondBaseline.length) * 2);
                assertTrue("Cached timing exceeded forced-reload variability",
                        Math.abs(cached.length - secondBaseline.length) <= tolerance);
            }
        }
    }

    @Test
    public void completeCatalogAndAllBundledVariantsAreSelectable() throws Exception {
        synchronized (CoreState.LOCK) {
            Method enumeration = SpeechSynthesis.class.getDeclaredMethod("nativeGetAvailableVoices");
            enumeration.setAccessible(true);
            String[] raw = (String[]) enumeration.invoke(engine);
            assertNotNull(raw);
            assertEquals("All bundled non-MBROLA base voices", 55, raw.length / 4);
            File[] variants = new File(CheckVoiceData.getDataPath(storage), "voices/!v").listFiles();
            assertNotNull(variants);
            int count = 0;
            for (File file : variants) {
                if (!file.isFile()) continue;
                assertTrue(file.getName(), engine.setVoice(polish,
                        new VoiceVariant(file.getName(), SpeechSynthesis.AGE_ANY)));
                // Flush the normal queued voice update before another selection.
                assertTrue(engine.synthesize("", false));
                count++;
            }
            assertEquals(104, count);
            CoreState.invalidateVoice();
        }
    }

    @Test
    public void compareIndependentProcessHistory() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String mode = arguments.getString("voiceReuseMode");
        org.junit.Assume.assumeNotNull(mode);
        int caseId = Integer.parseInt(arguments.getString("comparisonCase", "0"));
        String[] texts = {"Pierwszy dzień. Sześćset osób.", "Ala ma kota, a kot ma miskę.",
                "Today is a good day. One, two, three.",
                "bezinteresowna zidentyfikowana osoba", "A short ordinary voice sample"};
        String[] variants = {"male", "f3", "m2", "female-young", "fast"};
        int textIndex = caseId / 3;
        int rateMode = caseId % 3;
        int pitch = rateMode == 0 ? 50 : 63;
        int modulation = rateMode == 0 ? 50 : 72;
        Voice voice = textIndex == 2 || textIndex == 4 ? english : polish;
        VoiceVariant variant = VoiceVariant.parseVoiceVariant(variants[textIndex]);
        File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), "r38-independent-audio");
        assertTrue(output.isDirectory() || output.mkdirs());
        // Both fresh processes have identical history; the measured request also
        // changes pitch/modulation/rate from this known unaccelerated warmup.
        byte[] warmup = render(voice, variant, texts[textIndex], true, 42, 30, 0);
        byte[] measured = render(voice, variant, texts[textIndex], mode.equals("reload"), pitch, modulation, rateMode);
        FileUtils.write(new File(output, caseId + "-" + mode + "-warmup.pcm"), warmup);
        FileUtils.write(new File(output, caseId + "-" + mode + ".pcm"), measured);
    }

    @Test
    public void unavailableMetadataCanRetryWithoutReinitializingTheCore() throws Exception {
        Field field = SpeechSynthesis.class.getDeclaredField("sVoiceData");
        field.setAccessible(true);
        Object previous = field.get(null);
        int modulation = engine.PitchRange.getValue();
        try {
            engine.PitchRange.setValue(27);
            field.set(null, null);
            SpeechSynthesis retry = new SpeechSynthesis(storage, null);
            assertTrue(retry.getSampleRate() > 0);
            assertFalse(retry.getAvailableVoices().isEmpty());
            assertEquals(27, engine.PitchRange.getValue());
        } finally {
            field.set(null, previous);
            engine.PitchRange.setValue(modulation);
        }
    }

    @Test
    public void ssmlAndEmbeddedControlsInvalidateTheNextSelection() {
        VoiceVariant variant = VoiceVariant.parseVoiceVariant("male");
        assertTrue(engine.setVoice(polish, variant));
        parameters(50, 50, false);
        assertTrue(engine.synthesize("<speak><voice xml:lang='en'>Hello.</voice></speak>", true));
        assertNotEquals(CoreState.revision.get(), CoreState.selectedRevision);
        assertTrue(engine.setVoice(polish, variant));
        String selection = CoreState.selectedVoice;
        assertTrue(engine.synthesize("Test \u0001" + "60p głosu.", false));
        assertNotEquals(CoreState.revision.get(), CoreState.selectedRevision);
        assertTrue(engine.setVoice(polish, variant));
        assertNotSame(selection, CoreState.selectedVoice);
    }

    @Test
    public void stopDoesNotWaitForSynthesisAndInvalidatesSelection() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        SpeechSynthesis blocking = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] data) {
                entered.countDown();
                try { assertTrue(stopped.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            @Override public void onSynthDataComplete() { }
            @Override public void onSynthWordBoundary(int position, int length, int frame) { }
        });
        assertTrue(blocking.setVoice(polish, VoiceVariant.parseVoiceVariant("male")));
        Thread synthesis = new Thread(() -> {
            try { blocking.synthesize("Krótka próba anulowania zwykłej wypowiedzi.", false); }
            catch (Throwable failure) { error.set(failure); }
        });
        synthesis.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            blocking.stop();
        } finally {
            stopped.countDown();
            synthesis.join(5000);
        }
        assertFalse(synthesis.isAlive());
        assertNull(error.get());
        assertNotEquals(CoreState.revision.get(), CoreState.selectedRevision);
        render(polish, VoiceVariant.parseVoiceVariant("male"), "Mowa działa ponownie.", false, 50, 50, 0);
    }

    @Test
    public void unchangedDataDoesNotRefreshAndImportsInvalidateSelection() throws Exception {
        File marker = new File(CheckVoiceData.getDataPath(storage), "version");
        File dictionary = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
        long markerTime = marker.lastModified();
        long dictionaryTime = dictionary.lastModified();
        byte[] original = FileUtils.readBinary(dictionary);
        assertFalse(CheckVoiceData.canUpgradeResources(storage));
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        assertEquals(markerTime, marker.lastModified());
        assertEquals(dictionaryTime, dictionary.lastModified());
        File source = new File(storage.getCacheDir(), "pl_dict");
        File retained = new File(storage.getDir("imported_dictionaries", Context.MODE_PRIVATE), "pl_dict");
        byte[] previousImport = retained.isFile() ? FileUtils.readBinary(retained) : null;
        try {
            FileUtils.write(source, original);
            assertTrue(engine.setVoice(polish, VoiceVariant.parseVoiceVariant("male")));
            CheckVoiceData.installImportedDictionary(storage, source);
            assertNotEquals(CoreState.revision.get(), CoreState.selectedRevision);
            render(polish, VoiceVariant.parseVoiceVariant("male"), "Słownik po imporcie.", false, 50, 50, 0);
            assertArrayEquals(original, FileUtils.readBinary(dictionary));
        } finally {
            if (previousImport != null) FileUtils.write(retained, previousImport);
            else assertTrue(!retained.exists() || retained.delete());
            assertTrue(!source.exists() || source.delete());
            CoreState.invalidateVoice();
        }
    }

    @Test
    public void unreadableInstalledMarkerRequestsRecovery() throws Exception {
        File marker = new File(CheckVoiceData.getDataPath(storage), "version");
        byte[] contents = FileUtils.readBinary(marker);
        try {
            assertTrue(marker.delete());
            assertTrue(marker.mkdir());
            assertTrue(CheckVoiceData.canUpgradeResources(storage));
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            assertTrue(marker.isFile());
            assertFalse(CheckVoiceData.canUpgradeResources(storage));
        } finally {
            assertTrue(marker.delete());
            FileUtils.write(marker, contents);
        }
    }

    private TtsService service() throws Exception {
        TtsService service = new TtsService() {
            @Override protected int selectLanguageWithFallback(String language, String country, String variant) {
                mMatchingVoice = polish;
                return TextToSpeech.SUCCESS;
            }
        };
        Field context = TtsService.class.getDeclaredField("storageContext");
        context.setAccessible(true);
        context.set(null, storage);
        // Use the service's real callback path with a real core, without binding
        // another Android service in this deterministic lifecycle test.
        Field callback = TtsService.class.getDeclaredField("mSynthCallback");
        callback.setAccessible(true);
        Field nativeEngine = TtsService.class.getDeclaredField("mEngine");
        nativeEngine.setAccessible(true);
        nativeEngine.set(service, new SpeechSynthesis(storage,
                (SpeechSynthesis.SynthReadyCallback) callback.get(service)));
        return service;
    }

    private static class Output implements SynthesisCallback {
        int startResult = TextToSpeech.SUCCESS;
        int starts, bytes, completions, errors;
        @Override public int getMaxBufferSize() { return 8192; }
        @Override public int start(int rate, int format, int channels) { starts++; return startResult; }
        @Override public int audioAvailable(byte[] buffer, int offset, int length) { bytes += length; return 0; }
        @Override public int done() { completions++; return 0; }
        @Override public void error() { errors++; }
        @Override public void error(int code) { errors++; }
        @Override public boolean hasStarted() { return starts > 0; }
        @Override public boolean hasFinished() { return completions > 0; }
        @Override public void rangeStart(int frame, int start, int end) { }
    }

    @Test
    public void nullTextAndRejectedStartDoNotSynthesize() throws Exception {
        TtsService service = service();
        Output missing = new Output();
        service.onSynthesizeText(new SynthesisRequest((CharSequence) null, new Bundle()), missing);
        assertEquals(0, missing.starts);
        assertEquals(1, missing.errors);
        Output rejected = new Output();
        rejected.startResult = TextToSpeech.ERROR;
        service.onSynthesizeText(new SynthesisRequest("Test.", new Bundle()), rejected);
        assertEquals(1, rejected.starts);
        assertEquals(0, rejected.bytes);
        assertEquals(0, rejected.completions);
        assertEquals(0, rejected.errors);
    }

    @Test
    public void completedAndRejectedAudioReleaseRequestReferences() throws Exception {
        TtsService service = service();
        Output normal = new Output();
        service.onSynthesizeText(new SynthesisRequest("Krótka próba.", new Bundle()), normal);
        assertEquals(1, normal.completions);
        assertTrue(normal.bytes > 0);
        Output rejectedAudio = new Output() {
            @Override public int getMaxBufferSize() { return 0; }
        };
        service.onSynthesizeText(new SynthesisRequest("Krótka próba.", new Bundle()), rejectedAudio);
        assertEquals(1, rejectedAudio.errors);
        try {
            service.onSynthesizeText(new SynthesisRequest("Krótka próba.", new Bundle()), new Output() {
                @Override public int done() { throw new IllegalStateException("Expected output failure"); }
            });
            fail("Expected callback failure");
        } catch (IllegalStateException expected) {
            assertEquals("Expected output failure", expected.getMessage());
        }
        for (String name : new String[] {"mSynthText", "mCallback"}) {
            Field field = TtsService.class.getDeclaredField(name);
            field.setAccessible(true);
            assertNull(name, field.get(service));
        }
    }
}
