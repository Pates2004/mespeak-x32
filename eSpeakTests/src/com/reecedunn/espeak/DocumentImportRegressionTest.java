package com.reecedunn.espeak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceFragment;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.reecedunn.espeak.preference.ImportVoicePreference;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DocumentImportRegressionTest {
    private void awaitPreferences(ActivityScenario<TtsSettingsActivity> screen) throws Exception {
        AtomicBoolean ready = new AtomicBoolean();
        for (int i = 0; i < 100 && !ready.get(); ++i) {
            screen.onActivity(activity -> {
                PreferenceFragment fragment = (PreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
                ready.set(fragment != null && fragment.findPreference("speech_preview") != null);
            });
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue(ready.get());
    }
    private void awaitAccessibleMessage(String expected) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        for (int i = 0; i < 100; ++i) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null && !root.findAccessibilityNodeInfosByText(expected).isEmpty()) return;
            Thread.sleep(100);
        }
        fail("Expected accessible import result: " + expected);
    }
    private Uri document(String name) {
        return Uri.parse("content://" + InstrumentationRegistry.getInstrumentation().getContext()
            .getPackageName() + ".dictionary-test/" + name);
    }

    @Test public void documentPickerImportsAliasAndAnnouncesSuccess() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        File active = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
        File retained = new File(storage.getDir("imported_dictionaries", 0), "pl_dict");
        byte[] oldActive = FileUtils.readBinary(active);
        byte[] oldRetained = retained.exists() ? FileUtils.readBinary(retained) : null;
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String fixture = InstrumentationRegistry.getArguments().getString("dictionaryFixture");
        byte[] imported = fixture == null ? oldActive : FileUtils.readBinary(
            new File(instrumentation.getTargetContext().getCacheDir(), fixture));
        // Transport an actual valid dictionary, never arbitrary bytes into the
        // native-facing active data path. No duplicate binary fixture is tracked.
        try (OutputStream output = instrumentation.getContext().getContentResolver()
                .openOutputStream(document("pl.dict"))) {
            assertNotNull(output);
            output.write(imported);
        }
        AtomicReference<Intent> observed = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())) return null;
                observed.set(intent);
                return new Instrumentation.ActivityResult(Activity.RESULT_OK,
                    new Intent().setData(document("pl.dict")));
            }
        };
        instrumentation.addMonitor(monitor);
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            awaitPreferences(screen);
            screen.onActivity(activity -> {
                try {
                    ImportVoicePreference preference = new ImportVoicePreference(activity);
                    Method click = ImportVoicePreference.class.getDeclaredMethod("onClick");
                    click.setAccessible(true);
                    click.invoke(preference);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            assertNotNull(observed.get());
            assertTrue(observed.get().hasCategory(Intent.CATEGORY_OPENABLE));
            assertEquals("*/*", observed.get().getType());
            AtomicReference<String> message = new AtomicReference<>();
            screen.onActivity(activity -> message.set(activity.getString(R.string.import_voice_succeeded)));
            awaitAccessibleMessage(message.get());
            assertArrayEquals(imported, FileUtils.readBinary(active));
            assertArrayEquals(imported, FileUtils.readBinary(retained));
            synthesizeOrdinarySample(storage);
            CheckVoiceData.installImportedDictionary(storage, "pl_dict", oldActive);
            assertArrayEquals(oldActive, FileUtils.readBinary(active));
            synthesizeOrdinarySample(storage);
        } finally {
            instrumentation.removeMonitor(monitor);
            instrumentation.getContext().getContentResolver().delete(document("pl.dict"), null, null);
            synchronized (CheckVoiceData.class) {
                synchronized (CoreState.LOCK) {
                    FileUtils.write(active, oldActive);
                    if (oldRetained != null) FileUtils.write(retained, oldRetained);
                    else assertTrue(!retained.exists() || retained.delete());
                    CoreState.invalidateVoice();
                }
            }
        }
    }

    private void synthesizeOrdinarySample(Context storage) {
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        AtomicInteger completions = new AtomicInteger();
        SpeechSynthesis engine = new SpeechSynthesis(storage, new SpeechSynthesis.SynthReadyCallback() {
            @Override public void onSynthDataReady(byte[] data) { audio.write(data, 0, data.length); }
            @Override public void onSynthDataComplete() { completions.incrementAndGet(); }
            @Override public void onSynthWordBoundary(int position, int length, int frame) { }
        });
        Voice polish = null;
        for (Voice voice : engine.getAvailableVoices()) if ("pl".equals(voice.name)) polish = voice;
        assertNotNull(polish);
        synchronized (CoreState.LOCK) {
            assertTrue(engine.setVoice(polish, VoiceVariant.parseVoiceVariant("male")));
            engine.Rate.setValue(175);
            engine.setSmoothRate(175);
            engine.Pitch.setValue(50);
            engine.PitchRange.setValue(50);
            engine.Volume.setValue(100);
            assertTrue(engine.synthesize("Monika słucha muzyki. Trzydzieści, czterdzieści, dwieście.", false));
        }
        assertEquals(1, completions.get());
        assertTrue(audio.size() > 1000);
    }

    @Test public void recreatedSettingsReportsUnavailableDocumentWithoutChangingDictionary() throws Exception {
        Context storage = EspeakApp.getStorageContext();
        assertTrue(CheckVoiceData.ensureVoiceData(storage));
        File active = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
        byte[] original = FileUtils.readBinary(active);
        try (ActivityScenario<TtsSettingsActivity> screen = ActivityScenario.launch(TtsSettingsActivity.class)) {
            awaitPreferences(screen);
            screen.recreate();
            awaitPreferences(screen);
            AtomicReference<String> message = new AtomicReference<>();
            screen.onActivity(activity -> {
                message.set(activity.getString(R.string.import_voice_failed));
                activity.onActivityResult(ImportVoicePreference.REQUEST_IMPORT_DICTIONARY,
                    Activity.RESULT_OK, new Intent().setData(document("unavailable_dict")));
            });
            awaitAccessibleMessage(message.get());
            assertArrayEquals(original, FileUtils.readBinary(active));
        }
    }
}
