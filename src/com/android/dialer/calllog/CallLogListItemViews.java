/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.calllog;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.PhoneCallDetailsViews;
import com.android.dialer.R;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The secondary action view, which includes both the vertical divider line and
     *  the action button itself.  Used so that the button and divider line can be
     *  made visible/hidden as a whole. */
    public final View secondaryActionView;
    /** The secondary action button on the entry. */
    public final ImageView secondaryActionButtonView;
    /** The sub icon to mark the call action. */
    public final ImageView subIconView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header of a section. */
    public final TextView listHeaderTextView;

    private CallLogListItemViews(QuickContactBadge quickContactView, View primaryActionView,
            View secondaryActionView, ImageView secondaryActionButtonView,
            ImageView subIconView, PhoneCallDetailsViews phoneCallDetailsViews,
            TextView listHeaderTextView) {
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.secondaryActionView = secondaryActionView;
        this.secondaryActionButtonView = secondaryActionButtonView;
        this.subIconView = subIconView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.listHeaderTextView = listHeaderTextView;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                view.findViewById(R.id.secondary_action_view),
                (ImageView) view.findViewById(R.id.secondary_action_icon),
                (ImageView) view.findViewById(R.id.call_action_sub_icon),
                PhoneCallDetailsViews.fromView(view),
                (TextView) view.findViewById(R.id.call_log_header));
    }

    @NeededForTesting
    public static CallLogListItemViews createForTest(Context context) {
        return new CallLogListItemViews(
                new QuickContactBadge(context),
                new View(context),
                new View(context),
                new ImageView(context),
                new ImageView(context),
                PhoneCallDetailsViews.createForTest(context),
                new TextView(context));
    }
}
