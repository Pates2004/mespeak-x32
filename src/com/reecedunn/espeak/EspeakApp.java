/*
 * Copyright (C) 2022 Beka Gozalishvili
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

package com.reecedunn.espeak;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class EspeakApp extends Application {
    public static final String PREF_SHOW_LAUNCHER = "show_launcher";
    private static Context storageContext;

    public void onCreate() {
        super.onCreate();
        Context appContext = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            EspeakApp.storageContext = appContext.createDeviceProtectedStorageContext();
        }
        else {
            EspeakApp.storageContext = appContext;
        }
    }

    public static Context getStorageContext() {
        return EspeakApp.storageContext;
    }

    /**
     * Toggles only the launcher alias. The TTS service and its settings entry
     * remain registered with Android when the application icon is hidden.
     */
    public static boolean setLauncherVisible(Context context, boolean visible) {
        final ComponentName launcher = new ComponentName(
                context.getPackageName(), EspeakApp.class.getPackage().getName() + ".Launcher");
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    launcher,
                    visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
