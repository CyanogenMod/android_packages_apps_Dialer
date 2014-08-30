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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemProperties;
import android.provider.CallLog.Calls;
import android.util.AttributeSet;
import android.view.View;

import com.android.contacts.common.test.NeededForTesting;
import com.android.dialer.R;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
    private Resources mResources;
    private int mWidth;
    private int mHeight;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = new Resources(context);
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + mResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    @NeededForTesting
    public int getCount() {
        return mCallTypes.size();
    }

    @NeededForTesting
    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return mResources.incoming;
            case Calls.OUTGOING_TYPE:
                return mResources.outgoing;
            case Calls.MISSED_TYPE:
                return mResources.missed;
            case Calls.VOICEMAIL_TYPE:
                return mResources.voicemail;
            //add for csvt call log type
            case CallTypeHelper.INCOMING_CSVT_TYPE:
                if (isVTSupported()) {
                    return mResources.csvt_incoming;
                } else {
                    return mResources.incoming;
                }
            case CallTypeHelper.OUTGOING_CSVT_TYPE:
                if (isVTSupported()) {
                    return mResources.csvt_outgoing;
                } else {
                    return mResources.outgoing;
                }
            case CallTypeHelper.MISSED_CSVT_TYPE:
                if (isVTSupported()) {
                    return mResources.csvt_missed;
                } else {
                    return mResources.missed;
                }
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return mResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + mResources.iconMargin;
        }
    }

    private static class Resources {
        public final Drawable incoming;
        public final Drawable outgoing;
        public final Drawable missed;
        //add for csvt call log type
        public final Drawable csvt_incoming;
        public final Drawable csvt_outgoing;
        public final Drawable csvt_missed;
        public final Drawable voicemail;
        public final int iconMargin;

        public Resources(Context context) {
            final android.content.res.Resources r = context.getResources();
            incoming = r.getDrawable(R.drawable.ic_call_incoming_holo_dark);
            outgoing = r.getDrawable(R.drawable.ic_call_outgoing_holo_dark);
            missed = r.getDrawable(R.drawable.ic_call_missed_holo_dark);
            //add for csvt call log type
            csvt_incoming = r.getDrawable(R.drawable.ic_video_incoming_holo_dark);
            csvt_outgoing = r.getDrawable(R.drawable.ic_video_outgoing_holo_dark);
            csvt_missed = r.getDrawable(R.drawable.ic_video_missed_holo_dark);
            voicemail = r.getDrawable(R.drawable.ic_call_voicemail_holo_dark);
            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }
    }

    //add  for csvt call log type
    private boolean isVTSupported() {
        return SystemProperties.getBoolean(
                "persist.radio.csvt.enabled"
        /* TelephonyProperties.PROPERTY_CSVT_ENABLED*/, false);
    }
}
