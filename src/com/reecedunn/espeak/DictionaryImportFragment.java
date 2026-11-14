package com.reecedunn.espeak;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Owns an in-flight import across configuration changes without retaining an Activity. */
public final class DictionaryImportFragment extends Fragment {
    private static final String TAG = "dictionary_import_operation";
    private static final String RESULT_TAG = "dictionary_import_result";
    private boolean running;
    private Boolean pendingResult;
    private volatile Thread worker;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRetainInstance(true);
    }

    @Override public void onResume() {
        super.onResume();
        // FragmentManager may still be dispatching its own lifecycle transaction.
        new Handler(Looper.getMainLooper()).post(this::deliverResult);
    }

    private static DictionaryImportFragment owner(Activity activity) {
        FragmentManager manager = activity.getFragmentManager();
        DictionaryImportFragment fragment = (DictionaryImportFragment) manager.findFragmentByTag(TAG);
        if (fragment == null) {
            fragment = new DictionaryImportFragment();
            manager.beginTransaction().add(fragment, TAG).commit();
            manager.executePendingTransactions();
        }
        return fragment;
    }

    public static boolean isImportRunning(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentByTag(TAG);
        return fragment instanceof DictionaryImportFragment && ((DictionaryImportFragment) fragment).running;
    }

    public static void reportFailure(Activity activity) {
        DictionaryImportFragment fragment = owner(activity);
        fragment.pendingResult = false;
        fragment.deliverResult();
    }

    public static void importDocument(Activity activity, Uri document) {
        DictionaryImportFragment fragment = owner(activity);
        Toast.makeText(activity, R.string.import_voice_working, Toast.LENGTH_SHORT).show();
        if (fragment.running) return;
        fragment.running = true;
        final Context application = activity.getApplicationContext();
        final DictionaryImportFragment operation = fragment;
        operation.worker = new Thread(() -> {
            boolean success = false;
            try {
                String name = null;
                try (Cursor cursor = application.getContentResolver().query(document,
                        new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                name = CheckVoiceData.importedDictionaryName(name);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = application.getContentResolver().openInputStream(document)) {
                    if (input == null) throw new IOException("Document is unavailable");
                    byte[] buffer = new byte[10240];
                    int count;
                    while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                }
                CheckVoiceData.installImportedDictionary(EspeakApp.getStorageContext(), name, bytes.toByteArray());
                application.sendBroadcast(new Intent(DownloadVoiceData.BROADCAST_LANGUAGES_UPDATED));
                success = true;
            } catch (Exception ignored) {
                // The retained outcome is delivered in the current Activity's language.
            }
            final boolean outcome = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                operation.running = false;
                operation.worker = null;
                operation.pendingResult = outcome;
                operation.deliverResult();
            });
        }, "dictionary-import");
        operation.worker.start();
    }

    private void deliverResult() {
        Activity activity = getActivity();
        if (pendingResult == null || !isResumed() || activity == null
                || activity.isFinishing() || activity.isDestroyed()) return;
        FragmentManager manager = getFragmentManager();
        if (manager == null || (Build.VERSION.SDK_INT >= 26 && manager.isStateSaved())) return;
        if (manager.findFragmentByTag(RESULT_TAG) != null) return;
        ResultDialog dialog = new ResultDialog();
        Bundle arguments = new Bundle();
        arguments.putBoolean("success", pendingResult);
        dialog.setArguments(arguments);
        dialog.show(manager, RESULT_TAG);
        manager.executePendingTransactions();
        pendingResult = null;
    }

    /** A normal platform dialog also restores an already displayed result on rotation. */
    public static final class ResultDialog extends DialogFragment {
        @Override public Dialog onCreateDialog(Bundle state) {
            int message = getArguments().getBoolean("success")
                ? R.string.import_voice_succeeded : R.string.import_voice_failed;
            return new AlertDialog.Builder(getActivity()).setTitle(R.string.import_voice_title)
                .setMessage(message).setPositiveButton(android.R.string.ok, null).create();
        }
    }
}
