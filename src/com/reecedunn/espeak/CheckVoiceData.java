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
        try {
            final String version = FileUtils.read(context.getResources().openRawResource(R.raw.espeakdata_version));
            final String installedVersion = FileUtils.read(new File(getDataPath(context), "version"));
            return !version.equals(installedVersion);
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized boolean extractVoiceData(Context context) {
        final File dataPath = getDataPath(context);
        final InputStream stream = context.getResources().openRawResource(R.raw.espeakdata);
        final ZipInputStream zipStream = new ZipInputStream(new BufferedInputStream(stream));
        final File outputDir = dataPath.getParentFile();

        try {
            preserveLegacyImportedDictionaries(context);
            FileUtils.rmdir(dataPath);
            final String canonicalOutputDirPath = outputDir.getCanonicalPath() + File.separator;
            final byte[] buffer = new byte[10240];
            int bytesRead;
            ZipEntry entry;

            while ((entry = zipStream.getNextEntry()) != null) {
                final File file = new File(outputDir, entry.getName());
                if (!file.getCanonicalPath().startsWith(canonicalOutputDirPath)) {
                    throw new SecurityException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }
                file.getParentFile().mkdirs();
                final FileOutputStream outputStream = new FileOutputStream(file);
                try {
                    while ((bytesRead = zipStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } finally {
                    outputStream.close();
                }
                zipStream.closeEntry();
            }

            restoreImportedDictionaries(context);
            final String version = FileUtils.read(
                context.getResources().openRawResource(R.raw.espeakdata_version));
            FileUtils.write(new File(getDataPath(context), "version"), version);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract voice data", e);
            return false;
        } finally {
            try {
                zipStream.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }

    private static File getImportedDictionaryPath(Context context) {
        return context.getDir("imported_dictionaries", MODE_PRIVATE);
    }

    public static synchronized void installImportedDictionary(Context context, File source)
            throws IOException {
        if (!source.isFile() || !source.getName().endsWith("_dict")) {
            throw new IOException("Not a dictionary file");
        }
        final byte[] contents = FileUtils.readBinary(source);
        final File retained = new File(getImportedDictionaryPath(context), source.getName());
        FileUtils.write(retained, contents);
        FileUtils.write(new File(getDataPath(context), source.getName()), contents);
    }

    private static void preserveLegacyImportedDictionaries(Context context) throws Exception {
        final List<File> installed = new ArrayList<File>();
        final File[] active = getDataPath(context).listFiles();
        if (active != null) installed.addAll(Arrays.asList(active));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && context.isDeviceProtectedStorage()) {
            final UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
            if (users != null && users.isUserUnlocked()) {
                // Older import screens used credential storage while the TTS
                // service used device storage. Migrate those imports too.
                final Context credentialContext =
                    context.createPackageContext(context.getPackageName(), 0);
                final File legacyPath = getDataPath(credentialContext);
                final File[] legacy = legacyPath.listFiles();
                if (legacy != null) installed.addAll(Arrays.asList(legacy));
            }
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
        final File retained = getImportedDictionaryPath(context);
        for (File dictionary : installed) {
            if (!dictionary.isFile() || !dictionary.getName().endsWith("_dict")) continue;
            final File saved = new File(retained, dictionary.getName());
            if (saved.exists()) continue;
            final byte[] oldBytes = FileUtils.readBinary(dictionary);
            if (Arrays.equals(oldBytes, bundled.get(dictionary.getName()))) continue;
            if ("pl_dict".equals(dictionary.getName()) && isPreviouslyBundledPolish(oldBytes)) {
                continue;
            }
            FileUtils.write(saved, oldBytes);
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

    private static void restoreImportedDictionaries(Context context) throws IOException {
        final File[] imported = getImportedDictionaryPath(context).listFiles();
        if (imported == null) return;
        for (File dictionary : imported) {
            if (!dictionary.isFile() || !dictionary.getName().endsWith("_dict")) continue;
            FileUtils.write(new File(getDataPath(context), dictionary.getName()),
                FileUtils.readBinary(dictionary));
        }
    }

    /** Ensures that bundled voice data is ready before the native engine starts. */
    public static synchronized boolean ensureVoiceData(Context context) {
        if (hasBaseResources(context) && !canUpgradeResources(context)) {
            return true;
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

        if (!hasBaseResources(storageContext) || canUpgradeResources(storageContext)) {
            // Android may issue CHECK_TTS_DATA before it binds TtsService on a
            // fresh installation. Install the bundled data here as well so
            // Settings does not cache an empty language list until reopened.
            if (!ensureVoiceData(storageContext)) {
                unavailableLanguages.add(Locale.ENGLISH.toString());
                returnResults(Engine.CHECK_VOICE_DATA_FAIL, availableLanguages, unavailableLanguages);
                return;
            }
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
