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

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.android.dialer.R;
import com.android.dialer.cmstats.DialerStats.Fields;

import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;

public class ReportingIntentService extends IntentService {
    /* package */ static final String TAG = "CMStats";

    private Tracker mTracker;

    public ReportingIntentService() {
        super(ReportingIntentService.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Get Tracker
        GoogleAnalytics ga = GoogleAnalytics.getInstance(ReportingIntentService.this);
        mTracker = ga.getTracker(getString(R.string.ga_trackingId));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // send individual events in background
        Bundle bundle = intent.getExtras();
        if (!bundle.isEmpty()) {
            String category = bundle.getString(Fields.EVENT_CATEGORY);
            String action = bundle.getString(Fields.EVENT_ACTION);
            String label = null;
            Long value = null;

            if (bundle.containsKey(Fields.EVENT_LABEL)) {
               label = bundle.getString(Fields.EVENT_LABEL);
            }
            if (bundle.containsKey(Fields.EVENT_VALUE)) {
                value = bundle.getLong(Fields.EVENT_VALUE);
            }

            mTracker.sendEvent(category, action, label, value);
        }
    }
}
