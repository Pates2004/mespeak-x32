package com.reecedunn.espeak;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReleaseArtifactTest {
    @Test
    public void signedReleaseSynthesizesOrdinaryPolishText() throws Exception {
        String enginePackage = InstrumentationRegistry.getArguments().getString("releasePackage");
        Assume.assumeNotNull(enginePackage);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo installed = context.getPackageManager().getPackageInfo(enginePackage, 0);
        assertEquals(BuildConfig.VERSION_CODE, installed.versionCode);
        assertEquals(0, installed.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicInteger init = new AtomicInteger(TextToSpeech.ERROR);
        TextToSpeech speech = new TextToSpeech(context, status -> {
            init.set(status);
            ready.countDown();
        }, enginePackage);
        File wave = File.createTempFile("release-speech-", ".wav", context.getCacheDir());
        try {
            assertTrue("Release engine initialization timed out", ready.await(15, TimeUnit.SECONDS));
            assertEquals(TextToSpeech.SUCCESS, init.get());
            assertTrue(speech.getEngines().stream().anyMatch(engine -> enginePackage.equals(engine.name)));
            assertTrue(speech.setLanguage(new Locale("pl", "PL")) >= TextToSpeech.LANG_AVAILABLE);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { success.incrementAndGet(); completed.countDown(); }
                @Override public void onError(String id) { errors.incrementAndGet(); completed.countDown(); }
            });
            assertEquals(TextToSpeech.SUCCESS, speech.synthesizeToFile(
                    "Pierwszy. Pięćdziesiąt, sześćdziesiąt, dziewięćdziesiąt. Sześćset.",
                    new Bundle(), wave, "signed-release-check"));
            assertTrue("Release synthesis timed out", completed.await(15, TimeUnit.SECONDS));
            assertEquals(0, errors.get());
            assertEquals(1, success.get());
            assertTrue(wave.length() > 1000);
            boolean nonzero = false;
            try (FileInputStream input = new FileInputStream(wave)) {
                byte[] header = new byte[44];
                assertEquals(44, input.read(header));
                assertEquals('R', header[0]);
                assertEquals('W', header[8]);
                int sample;
                while ((sample = input.read()) >= 0) nonzero |= sample != 0;
            }
            assertTrue("Release audio must not be silent", nonzero);
        } finally {
            speech.shutdown();
            assertTrue(!wave.exists() || wave.delete());
        }
    }
}
