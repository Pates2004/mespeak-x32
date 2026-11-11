/*
 * Copyright (C) 2022 Beka Gozalishvili
 * Copyright (C) 2012-2013 Reece H. Dunn
 * Copyright (C) 2009 The Android Open Source Project
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
 * This Activity is used by Android to get the list of languages to display
 * to the user when selecting the text-to-speech language. This is by locale,
 * not voice name.
 */

package com.reecedunn.espeak;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech.Engine;
import android.util.Log;

import com.reecedunn.espeak.SpeechSynthesis.SynthReadyCallback;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CheckVoiceData extends Activity {
    private static final String TAG = "eSpeakTTS";
    static final String PREF_LEGACY_IMPORTS_HANDLED = "legacy_imports_handled";

    /** Resources required for eSpeak to run correctly. */
    private static final String[] BASE_RESOURCES = {
        "version",
        "intonations",
        "phondata",
        "phonindex",
        "phontab",
        "en_dict",
    };

    // Git blob IDs of Polish dictionaries shipped in earlier meSpeak releases.
    // An older bundled pl_dict must be replaced, while a user override must
    // survive the data refresh even when it has the same filename.
    private static final String[] OLD_POLISH_DICTIONARIES = {
        "ed0d43f28c335890e89b32997c1197c2c3435216",
        "1e3683d0a1a68855b3a27e895a3fe0a2bd918b64",
        "dd07b632b200f42c100bb3c0481aacc309c406cd",
        "ee98069936bdb9c1e098af8cbbb504cbc4b4bfc7",
        "a1160dfdbdf5df60815391d81e67ac9d682af25e",
        "068f7cbff5f1f8a013018c9a4bf7e04a85965762",
        "392115ca715cd80c10aa2b9e182d5ec0b361d714",
        "027d2ff8e4fda073ce548b1658a44b2ebac03bb3",
        "501e07b3e011d3b68ed1d89c85edae9f5667d8d5",
        "3ec56cf1c39debd6624d4070a22ae4f0139500d0",
    };

    public static File getDataPath(Context context) {
        return new File(context.getDir("voices", MODE_PRIVATE), "espeak-ng-data");
    }

    public static boolean hasBaseResources(Context context) {
        final File dataPath = getDataPath(context);

        for (String resource : BASE_RESOURCES) {
            final File resourceFile = new File(dataPath, resource);

            if (!resourceFile.exists()) {
                Log.e(TAG, "Missing base resource: " + resourceFile.getPath());
                return false;
            }
        }

        return true;
    }

    public static boolean canUpgradeResources(Context context) {
        final String version;
        try (InputStream bundled = context.getResources().openRawResource(R.raw.espeakdata_version)) {
            version = FileUtils.read(bundled);
        } catch (Exception e) {
            Log.e(TAG, "Cannot read bundled data version", e);
            return false;
        }
        try {
            return !version.equals(FileUtils.read(new File(getDataPath(context), "version")));
        } catch (IOException e) {
            // A missing/unreadable installed marker is not evidence of current
            // data. Recovery preserves retained imports under the usual policy.
            return true;
        }
    }

    public static synchronized boolean extractVoiceData(Context context) {
        synchronized (CoreState.LOCK) {
            return extractVoiceDataLocked(context);
        }
    }

    private static boolean extractVoiceDataLocked(Context context) {
        final String version;
        try (InputStream marker = context.getResources().openRawResource(R.raw.espeakdata_version)) {
            version = FileUtils.read(marker);
        } catch (Exception e) {
            Log.e(TAG, "Cannot read bundled data version", e);
            return false;
        }
        final File dataPath = getDataPath(context);
        final FileTransaction transaction = transaction(context);
        try {
            recoverDataLocked(context);
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean preserveImports = prefs.getBoolean(
                EspeakApp.PREF_PRESERVE_IMPORTED_DICTIONARIES, true);
            final boolean migrationPending = !prefs.getBoolean(PREF_LEGACY_IMPORTS_HANDLED, false);
            final boolean scanLegacy = migrationPending
                && isLegacyStorageReady(context);
            transaction.begin(dataPath, getImportedDictionaryPath(context));
            final File outputDir = transaction.staged(0);
            final File retained = transaction.staged(1);
            FileTransaction.mkdir(outputDir);
            FileTransaction.mkdir(retained);
            copyTree(getImportedDictionaryPath(context), retained);
            if (preserveImports) {
                if (migrationPending) {
                    preserveLegacyImportedDictionaries(context, scanLegacy, retained);
                }
            } else {
                final File[] saved = retained.listFiles();
                if (saved == null) throw new IOException("Cannot inspect staged imports");
                for (File dictionary : saved) {
                    if (dictionary.isFile() && dictionary.getName().endsWith("_dict")) {
                        FileTransaction.remove(dictionary);
                    }
                }
            }
            final String canonicalOutputDirPath = outputDir.getCanonicalPath() + File.separator;
            final byte[] buffer = new byte[10240];
            int bytesRead;
            ZipEntry entry;
            try (ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(
                    context.getResources().openRawResource(R.raw.espeakdata)))) {
                while ((entry = zipStream.getNextEntry()) != null) {
                    final String prefix = "espeak-ng-data/";
                    if ("espeak-ng-data/".equals(entry.getName())) continue;
                    if (!entry.getName().startsWith(prefix)) throw new IOException("Unexpected archive entry");
                    final File file = new File(outputDir, entry.getName().substring(prefix.length()));
                    if (!file.getCanonicalPath().startsWith(canonicalOutputDirPath)) {
                        throw new SecurityException("Zip entry outside target dir: " + entry.getName());
                    }
                    // Commit the data marker only after imports have been
                    // restored or discarded successfully.
                    if ("espeak-ng-data/version".equals(entry.getName())) {
                        zipStream.closeEntry();
                        continue;
                    }
                    if (entry.isDirectory()) {
                        FileTransaction.mkdir(file);
                        continue;
                    }
                    FileTransaction.mkdir(file.getParentFile());
                    final FileOutputStream outputStream = new FileOutputStream(file);
                    try {
                        while ((bytesRead = zipStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.getFD().sync();
                    } finally {
                        outputStream.close();
                    }
                    zipStream.closeEntry();
                }
            }
            if (preserveImports) restoreImportedDictionaries(retained, outputDir);
            writeDurable(new File(outputDir, "version"), version.getBytes("UTF-8"));
            for (String resource : BASE_RESOURCES) {
                if (!new File(outputDir, resource).isFile()) throw new IOException("Incomplete bundled data");
            }
            transaction.commit();
            CoreState.invalidateVoice();
            if (scanLegacy && !prefs.edit().putBoolean(PREF_LEGACY_IMPORTS_HANDLED, true).commit()) {
                // Data publication already succeeded. Retrying the idempotent
                // migration later is safe; reporting a failed update here would
                // incorrectly suggest that the old dictionary is still active.
                Log.w(TAG, "Legacy migration metadata will be retried");
            }
            return true;
        } catch (Exception e) {
            try { transaction.abortPrepared(); } catch (IOException recovery) { e.addSuppressed(recovery); }
            Log.e(TAG, "Failed to extract voice data", e);
            return false;
        }
    }

    private static File getImportedDictionaryPath(Context context) {
        return context.getDir("imported_dictionaries", MODE_PRIVATE);
    }

    public static synchronized void installImportedDictionary(Context context, File source)
            throws IOException {
        if (!source.isFile()) throw new IOException("Not a dictionary file");
        installImportedDictionary(context, source.getName(), FileUtils.readBinary(source));
    }

    public static String importedDictionaryName(String name) throws IOException {
        if ("pl.dict".equals(name)) return "pl_dict";
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]*_dict")) {
            throw new IOException("Not a dictionary filename");
        }
        return name;
    }

    public static synchronized void installImportedDictionary(Context context, String name, byte[] contents)
            throws IOException {
        name = importedDictionaryName(name);
        if (contents == null || contents.length == 0) throw new IOException("Empty dictionary");
        synchronized (CoreState.LOCK) {
            recoverDataLocked(context);
            if (!hasBaseResources(context)) throw new IOException("Voice data unavailable");
            final FileTransaction transaction = transaction(context);
            try {
                transaction.begin(new File(getImportedDictionaryPath(context), name),
                    new File(getDataPath(context), name));
                writeDurable(transaction.staged(0), contents);
                writeDurable(transaction.staged(1), contents);
                transaction.commit();
                CoreState.invalidateVoice();
            } catch (IOException failure) {
                try { transaction.abortPrepared(); } catch (IOException recovery) { failure.addSuppressed(recovery); }
                throw failure;
            }
        }
    }

    private static boolean isLegacyStorageReady(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !context.isDeviceProtectedStorage()) {
            return true;
        }
        final UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return users != null && users.isUserUnlocked();
    }

    private static void preserveLegacyImportedDictionaries(Context context, boolean scanLegacy, File retained)
            throws Exception {
        final List<File> installed = new ArrayList<File>();
        final File[] active = getDataPath(context).listFiles();
        if (active != null) installed.addAll(Arrays.asList(active));
        if (scanLegacy && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && context.isDeviceProtectedStorage()) {
            // Older import screens used credential storage while the TTS
            // service used device storage. Migrate those imports once.
            final Context credentialContext =
                context.createPackageContext(context.getPackageName(), 0);
            final File legacyPath = getDataPath(credentialContext);
            final File[] legacy = legacyPath.listFiles();
            if (legacy != null) installed.addAll(Arrays.asList(legacy));
        }
        if (installed.isEmpty()) return;
        final Map<String, byte[]> bundled = new HashMap<String, byte[]>();
        final ZipInputStream archive = new ZipInputStream(new BufferedInputStream(
            context.getResources().openRawResource(R.raw.espeakdata)));
        try {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith("_dict")) continue;
                final String name = new File(entry.getName()).getName();
                final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                final byte[] buffer = new byte[10240];
                int count;
                while ((count = archive.read(buffer)) != -1) bytes.write(buffer, 0, count);
                bundled.put(name, bytes.toByteArray());
            }
        } finally {
            archive.close();
        }
        for (File dictionary : installed) {
            if (!dictionary.isFile() || !dictionary.getName().endsWith("_dict")) continue;
            final File saved = new File(retained, dictionary.getName());
            if (saved.exists()) continue;
            final byte[] oldBytes = FileUtils.readBinary(dictionary);
            if (Arrays.equals(oldBytes, bundled.get(dictionary.getName()))) continue;
            if ("pl_dict".equals(dictionary.getName()) && isPreviouslyBundledPolish(oldBytes)) {
                continue;
            }
            writeDurable(saved, oldBytes);
        }
    }

    private static boolean isPreviouslyBundledPolish(byte[] contents) throws Exception {
        final java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + contents.length + "\u0000").getBytes("UTF-8"));
        digest.update(contents);
        final byte[] hash = digest.digest();
        final StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        for (String bundledHash : OLD_POLISH_DICTIONARIES) {
            if (bundledHash.equals(hex.toString())) return true;
        }
        return false;
    }

    private static void restoreImportedDictionaries(File retained, File data) throws IOException {
        final File[] imported = retained.listFiles();
        if (imported == null) throw new IOException("Cannot inspect retained dictionaries");
        for (File dictionary : imported) {
            if (!dictionary.isFile() || !dictionary.getName().endsWith("_dict")) continue;
            writeDurable(new File(data, dictionary.getName()),
                FileUtils.readBinary(dictionary));
        }
    }

    private static void copyTree(File source, File target) throws IOException {
        if (!source.exists()) return;
        if (source.isDirectory()) {
            FileTransaction.mkdir(target);
            final File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot inspect dictionaries");
            for (File child : children) copyTree(child, new File(target, child.getName()));
        } else {
            writeDurable(target, FileUtils.readBinary(source));
        }
    }

    private static void writeDurable(File target, byte[] contents) throws IOException {
        FileTransaction.mkdir(target.getParentFile());
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(contents);
            output.getFD().sync();
        }
    }

    private static FileTransaction transaction(Context context) {
        return new FileTransaction(new File(context.getDir("dictionary_transactions", MODE_PRIVATE), "current"),
            getDataPath(context), getImportedDictionaryPath(context));
    }

    private static void recoverDataLocked(Context context) throws IOException {
        final FileTransaction transaction = transaction(context);
        if (transaction.isPending()) {
            try {
                if (transaction.recover()) CoreState.invalidateVoice();
            } catch (IOException failure) {
                CoreState.invalidateVoice();
                throw failure;
            }
        }
    }

    /** Ensures that bundled voice data is ready before the native engine starts. */
    public static synchronized boolean ensureVoiceData(Context context) {
        if (transaction(context).isPending()) {
            synchronized (CoreState.LOCK) {
                try { recoverDataLocked(context); }
                catch (IOException e) {
                    Log.e(TAG, "Failed to recover voice data", e);
                    return false;
                }
            }
        }
        if (hasBaseResources(context) && !canUpgradeResources(context)) {
            // Direct Boot may have delayed access to older credential storage.
            // Finish that one-time migration after unlock without reinstalling
            // the whole bundled voice archive.
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (prefs.getBoolean(PREF_LEGACY_IMPORTS_HANDLED, false)
                    || !isLegacyStorageReady(context)) return true;
            // Use the same publication transaction for delayed legacy migration.
            return extractVoiceData(context);
        }
        return extractVoiceData(context) && hasBaseResources(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context storageContext = EspeakApp.getStorageContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(storageContext);
        ArrayList<String> availableLanguages = new ArrayList<String>();
        ArrayList<String> unavailableLanguages = new ArrayList<String>();

        // Also recover pending publication before accepting a complete marker.
        // Android may check data before it binds TtsService on a fresh install.
        if (!ensureVoiceData(storageContext)) {
            unavailableLanguages.add(Locale.ENGLISH.toString());
            returnResults(Engine.CHECK_VOICE_DATA_FAIL, availableLanguages, unavailableLanguages);
            return;
        }

        final SpeechSynthesis engine = new SpeechSynthesis(storageContext, mSynthReadyCallback);
        final List<Voice> voices = LanguageSettings.filterVoices(engine.getAvailableVoices(), prefs);
        if (BuildConfig.DEBUG) {
            Set<String> selected = LanguageSettings.getSelectedLanguages(prefs);
            Log.i(TAG, "CheckVoiceData: selected=" + (selected == null ? "ALL" : selected.size()) + ", exposing=" + voices.size());
        }

        for (Voice voice : voices) {
            availableLanguages.add(voice.toString());
        }

        returnResults(Engine.CHECK_VOICE_DATA_PASS, availableLanguages, unavailableLanguages);
    }

    private void returnResults(int result, ArrayList<String> availableLanguages, ArrayList<String> unavailableLanguages) {
        final Intent returnData = new Intent();
        returnData.putStringArrayListExtra(Engine.EXTRA_AVAILABLE_VOICES, availableLanguages);
        returnData.putStringArrayListExtra(Engine.EXTRA_UNAVAILABLE_VOICES, unavailableLanguages);
        setResult(result, returnData);
        finish();
    }

    private final SynthReadyCallback mSynthReadyCallback = new SynthReadyCallback() {
        @Override
        public void onSynthDataReady(byte[] audioData) {
            // Do nothing.
        }

        @Override
        public void onSynthDataComplete() {
            // Do nothing.
        }

        @Override
        public void onSynthWordBoundary(int textPosition, int textLength, int markerInFrames) {
            // Do nothing.
        }
    };
}
