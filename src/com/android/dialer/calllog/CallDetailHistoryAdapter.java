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
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.android.contacts.common.CallUtil;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.google.common.collect.Lists;

import com.android.dialer.util.CallRecordingPlayer;
import com.android.services.callrecorder.common.CallRecording;
import com.android.services.callrecorder.CallRecorderService;
import com.android.services.callrecorder.CallRecordingDataStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for a ListView containing history items from the details of a call.
 */
public class CallDetailHistoryAdapter extends BaseAdapter {
    /** The top element is a blank header, which is hidden under the rest of the UI. */
    private static final int VIEW_TYPE_HEADER = 0;
    /** Each history item shows the detail of a call. */
    private static final int VIEW_TYPE_HISTORY_ITEM = 1;

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final CallTypeHelper mCallTypeHelper;
    private final PhoneCallDetails[] mPhoneCallDetails;

    /**
     * List of items to be concatenated together for duration strings.
     */
    private ArrayList<CharSequence> mDurationItems = Lists.newArrayList();

    private CallRecordingDataStore mCallRecordingDataStore;
    private CallRecordingPlayer mCallRecordingPlayer;

    public CallDetailHistoryAdapter(Context context, LayoutInflater layoutInflater,
            CallTypeHelper callTypeHelper, PhoneCallDetails[] phoneCallDetails,
            CallRecordingDataStore callRecordingDataStore,
            CallRecordingPlayer callRecordingPlayer) {
        mContext = context;
        mLayoutInflater = layoutInflater;
        mCallTypeHelper = callTypeHelper;
        mPhoneCallDetails = phoneCallDetails;
        mCallRecordingDataStore = callRecordingDataStore;
        mCallRecordingPlayer = callRecordingPlayer;
    }

    @Override
    public boolean isEnabled(int position) {
        // None of history will be clickable.
        return false;
    }

    @Override
    public int getCount() {
        return mPhoneCallDetails.length + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) {
            return null;
        }
        return mPhoneCallDetails[position - 1];
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return -1;
        }
        return position - 1;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_HEADER;
        }
        return VIEW_TYPE_HISTORY_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0) {
            final View header = convertView == null
                    ? mLayoutInflater.inflate(R.layout.call_detail_history_header, parent, false)
                    : convertView;
            return header;
        }

        // Make sure we have a valid convertView to start with
        final View result  = convertView == null
                ? mLayoutInflater.inflate(R.layout.call_detail_history_item, parent, false)
                : convertView;

        PhoneCallDetails details = mPhoneCallDetails[position - 1];
        CallTypeIconsView callTypeIconView =
                (CallTypeIconsView) result.findViewById(R.id.call_type_icon);
        TextView callTypeTextView = (TextView) result.findViewById(R.id.call_type_text);
        TextView dateView = (TextView) result.findViewById(R.id.date);
        TextView durationView = (TextView) result.findViewById(R.id.duration);

        int callType = details.callTypes[0];
        boolean isVideoCall = (details.features & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO
                && CallUtil.isVideoEnabled(mContext);

        callTypeIconView.clear();
        callTypeIconView.add(callType);
        callTypeIconView.setShowVideo(isVideoCall);
        callTypeTextView.setText(mCallTypeHelper.getCallTypeText(callType, isVideoCall));
        // Set the date.
        CharSequence dateValue = DateUtils.formatDateRange(mContext, details.date, details.date,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_YEAR);
        dateView.setText(dateValue);
        // Set the duration
        if (Calls.VOICEMAIL_TYPE == callType || CallTypeHelper.isMissedCallType(callType)) {
            durationView.setVisibility(View.GONE);
        } else {
            durationView.setVisibility(View.VISIBLE);
            durationView.setText(
                    formatDurationAndDataUsage(details.duration, details.dataUsage, details.durationType));
        }

        // do this synchronously to prevent recordings from "popping in"
        // after detail item is displayed
        if (CallRecorderService.isEnabled(mContext)) {
            mCallRecordingDataStore.open(mContext); // opens unless already open
            List<CallRecording> recordings =
                    mCallRecordingDataStore.getRecordings(details.number.toString(), details.date);

            ViewGroup playbackView =
                    (ViewGroup) result.findViewById(R.id.recording_playback_layout);
            playbackView.removeAllViews();
            for (CallRecording recording : recordings) {
                Button button = mCallRecordingPlayer.createPlaybackButton(mContext, recording);
                playbackView.addView(button);
            }
        }

        return result;
    }

    private CharSequence formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
    }

    /**
     * Formats a string containing the call duration and the data usage (if specified).
     *
     * @param elapsedSeconds Total elapsed seconds.
     * @param dataUsage Data usage in bytes, or null if not specified.
     * @return String containing call duration and data usage.
     */
    private CharSequence formatDurationAndDataUsage(long elapsedSeconds, Long dataUsage,
            int durationType) {
        CharSequence duration = formatDuration(elapsedSeconds);

        if (dataUsage != null) {
            mDurationItems.clear();
            mDurationItems.add(duration);
            mDurationItems.add(Formatter.formatShortFileSize(mContext, dataUsage));

            return DialerUtils.join(mContext.getResources(), mDurationItems);
        } else {
            boolean enabled = mContext.getResources().getBoolean(R.bool.call_durationtype_enabled);
            if (enabled) {
                switch (durationType) {
                    case Calls.DURATION_TYPE_ACTIVE:
                        return mContext.getString(R.string.call_duration_active) + duration;
                    case Calls.DURATION_TYPE_CALLOUT:
                        return mContext.getString(R.string.call_duration_call_out) + duration;
                    default:
                        return duration;
                }
            } else {
                return duration;
            }
        }
    }
}
