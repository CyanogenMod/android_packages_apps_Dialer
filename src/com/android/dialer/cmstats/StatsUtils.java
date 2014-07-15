
/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.dialer.cmstats;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

public class StatsUtils {
    private static final String STATS_PACKAGE = "com.cyngn.cmstats";

    public static boolean isStatsCollectionEnabled(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.STATS_COLLECTION, 1) != 0;
    }

    public static boolean isStatsPackageInstalled(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(STATS_PACKAGE, 0);
            return pi.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
