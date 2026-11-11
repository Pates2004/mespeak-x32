/*
 * Copyright (C) 2013 Reece H. Dunn
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

package com.reecedunn.espeak.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.Preference;
import android.provider.OpenableColumns;
import android.util.AttributeSet;
import android.widget.Toast;

import com.reecedunn.espeak.CheckVoiceData;
import com.reecedunn.espeak.DownloadVoiceData;
import com.reecedunn.espeak.EspeakApp;
import com.reecedunn.espeak.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Uses Android's document picker, including Downloads and document providers. */
public class ImportVoicePreference extends Preference {
    public static final int REQUEST_IMPORT_DICTIONARY = 2301;

    public ImportVoicePreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    public ImportVoicePreference(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public ImportVoicePreference(Context context) { this(context, null); }

    @Override
    protected void onClick() {
        try {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ((Activity) getContext()).startActivityForResult(intent, REQUEST_IMPORT_DICTIONARY);
        } catch (RuntimeException e) {
            showResult((Activity) getContext(), R.string.import_voice_failed);
        }
    }

    public static void onDocumentResult(Activity activity, int resultCode, Intent result) {
        if (resultCode != Activity.RESULT_OK) return;
        final Uri uri = result == null ? null : result.getData();
        if (uri == null) {
            showResult(activity, R.string.import_voice_failed);
            return;
        }
        Toast.makeText(activity, R.string.import_voice_working, Toast.LENGTH_SHORT).show();
        new AsyncTask<Void, Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... ignored) {
                try {
                    String name = null;
                    try (Cursor cursor = activity.getContentResolver().query(uri,
                            new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                    }
                    name = CheckVoiceData.importedDictionaryName(name);
                    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new IOException("Document is unavailable");
                        final byte[] buffer = new byte[10240];
                        int count;
                        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    }
                    CheckVoiceData.installImportedDictionary(EspeakApp.getStorageContext(), name,
                        bytes.toByteArray());
                    activity.sendBroadcast(new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            @Override protected void onPostExecute(Boolean success) {
                showResult(activity, success ? R.string.import_voice_succeeded : R.string.import_voice_failed);
            }
        }.execute();
    }

    private static void showResult(Activity activity, int message) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new AlertDialog.Builder(activity).setTitle(R.string.import_voice_title)
            .setMessage(message).setPositiveButton(android.R.string.ok, null).show();
    }
}
