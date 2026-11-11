package com.reecedunn.espeak;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DictionaryUpdateRegressionTest {
    private static final class InterruptedProcess extends Error { }

    private static final class IsolatedStorage extends ContextWrapper {
        private final File root;
        boolean failArchive;
        IsolatedStorage(Context context, File root) { super(context); this.root = root; }
        @Override public File getDir(String name, int mode) {
            File result = new File(root, name);
            assertTrue(result.isDirectory() || result.mkdirs());
            return result;
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences("r42-isolated-" + root.getName() + "-" + name, mode);
        }
        @Override public Resources getResources() {
            Resources original = super.getResources();
            if (!failArchive) return original;
            return new Resources(original.getAssets(), original.getDisplayMetrics(), original.getConfiguration()) {
                @Override public InputStream openRawResource(int id) throws NotFoundException {
                    InputStream input = original.openRawResource(id);
                    if (id != R.raw.espeakdata) return input;
                    return new FilterInputStream(input) {
                        private int remaining = 4096;
                        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                            if (remaining <= 0) throw new IOException("Injected read failure");
                            int count = super.read(bytes, offset, Math.min(length, remaining));
                            if (count > 0) remaining -= count;
                            return count;
                        }
                        @Override public int read() throws IOException {
                            if (--remaining <= 0) throw new IOException("Injected read failure");
                            return super.read();
                        }
                    };
                }
            };
        }
    }

    @Test public void failedRefreshKeepsActiveAndRetainedImportsEvenWhenPreservationIsOff() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "r42-refresh-failure");
        FileTransaction.remove(root);
        IsolatedStorage storage = new IsolatedStorage(EspeakApp.getStorageContext(), root);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        try {
            assertTrue(prefs.edit().clear().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, true).commit());
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            byte[] contents = "custom import retained across a failed update".getBytes(StandardCharsets.UTF_8);
            CheckVoiceData.installImportedDictionary(storage, "zz_r42_dict", contents);
            byte[] previousPolish = FileUtils.readBinary(new File(CheckVoiceData.getDataPath(storage), "pl_dict"));
            assertTrue(prefs.edit().putBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, false).commit());
            storage.failArchive = true;
            assertFalse(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(previousPolish, FileUtils.readBinary(new File(CheckVoiceData.getDataPath(storage), "pl_dict")));
            assertArrayEquals(contents, FileUtils.readBinary(new File(CheckVoiceData.getDataPath(storage), "zz_r42_dict")));
            assertArrayEquals(contents, FileUtils.readBinary(new File(storage.getDir("imported_dictionaries", 0), "zz_r42_dict")));
            assertFalse(prefs.getBoolean(EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true));
            storage.failArchive = false;
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertFalse(new File(CheckVoiceData.getDataPath(storage), "zz_r42_dict").exists());
            assertFalse(new File(storage.getDir("imported_dictionaries", 0), "zz_r42_dict").exists());
        } finally {
            prefs.edit().clear().commit();
            FileTransaction.remove(root);
        }
    }

    @Test public void startupRecoversBeforeTrustingExistingCompleteVersionMarker() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "r42-startup-recovery");
        FileTransaction.remove(root);
        IsolatedStorage storage = new IsolatedStorage(EspeakApp.getStorageContext(), root);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        try {
            assertTrue(prefs.edit().clear().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, true).commit());
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            File dictionary = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
            byte[] original = FileUtils.readBinary(dictionary);
            File retained = new File(storage.getDir("imported_dictionaries", 0), "pl_dict");
            File journal = new File(storage.getDir("dictionary_transactions", 0), "current");
            FileTransaction transaction = new FileTransaction(journal, phase -> {
                if ("activate:1".equals(phase)) throw new InterruptedProcess();
            }, CheckVoiceData.getDataPath(storage), retained.getParentFile());
            transaction.begin(retained, dictionary);
            FileUtils.write(transaction.staged(0), "interrupted new import");
            FileUtils.write(transaction.staged(1), "interrupted new import");
            try { transaction.commit(); fail(); } catch (InterruptedProcess expected) { }
            assertTrue(CheckVoiceData.hasBaseResources(storage));
            assertFalse(CheckVoiceData.canUpgradeResources(storage));
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            assertArrayEquals(original, FileUtils.readBinary(dictionary));
            assertFalse(retained.exists());
            assertFalse(journal.exists());
        } finally {
            prefs.edit().clear().commit();
            FileTransaction.remove(root);
        }
    }

    @Test public void plDotDictAliasReplacesBothCopiesAndSurvivesRefresh() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "r42-alias-import");
        FileTransaction.remove(root);
        IsolatedStorage storage = new IsolatedStorage(EspeakApp.getStorageContext(), root);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        try {
            assertTrue(prefs.edit().clear().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, true).commit());
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            byte[] dictionary = FileUtils.readBinary(new File(CheckVoiceData.getDataPath(storage), "pl_dict"));
            CheckVoiceData.installImportedDictionary(storage, "pl.dict", dictionary);
            assertArrayEquals(dictionary, FileUtils.readBinary(new File(storage.getDir("imported_dictionaries", 0), "pl_dict")));
            assertFalse(new File(CheckVoiceData.getDataPath(storage), "pl.dict").exists());
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(dictionary, FileUtils.readBinary(new File(CheckVoiceData.getDataPath(storage), "pl_dict")));
        } finally {
            prefs.edit().clear().commit();
            FileTransaction.remove(root);
        }
    }

    @Test public void previousBundledPolishIsNotMigratedAsAnOverride() throws Exception {
        String fixture = InstrumentationRegistry.getArguments().getString("previousDictionaryFixture");
        Assume.assumeNotNull(fixture);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] previous = FileUtils.readBinary(new File(context.getCacheDir(), fixture));
        File root = new File(context.getCacheDir(), "r42-previous-bundle");
        FileTransaction.remove(root);
        IsolatedStorage storage = new IsolatedStorage(EspeakApp.getStorageContext(), root);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storage);
        try {
            assertTrue(prefs.edit().clear().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, true).commit());
            assertTrue(CheckVoiceData.ensureVoiceData(storage));
            File active = new File(CheckVoiceData.getDataPath(storage), "pl_dict");
            byte[] bundled = FileUtils.readBinary(active);
            FileUtils.write(active, previous);
            assertTrue(prefs.edit().putBoolean(CheckVoiceData.PREF_LEGACY_IMPORTS_HANDLED, false).commit());
            assertTrue(CheckVoiceData.extractVoiceData(storage));
            assertArrayEquals(bundled, FileUtils.readBinary(active));
            assertFalse(new File(storage.getDir("imported_dictionaries", 0), "pl_dict").exists());
        } finally {
            prefs.edit().clear().commit();
            FileTransaction.remove(root);
        }
    }
}
