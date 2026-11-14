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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;

import com.reecedunn.espeak.DictionaryImportFragment;
import com.reecedunn.espeak.R;


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
        if (DictionaryImportFragment.isImportRunning((Activity) getContext())) {
            Toast.makeText(getContext(), R.string.import_voice_working, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ((Activity) getContext()).startActivityForResult(intent, REQUEST_IMPORT_DICTIONARY);
        } catch (RuntimeException e) {
            showFailure((Activity) getContext());
        }
    }

    public static void onDocumentResult(Activity activity, int resultCode, Intent result) {
        if (resultCode != Activity.RESULT_OK) return;
        final Uri uri = result == null ? null : result.getData();
        if (uri == null) {
            showFailure(activity);
            return;
        }
        DictionaryImportFragment.importDocument(activity, uri);
    }

    private static void showFailure(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        DictionaryImportFragment.reportFailure(activity);
    }
}
