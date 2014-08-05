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
import android.graphics.Color;
import android.os.AsyncTask;
import android.provider.CallLog.Calls;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import com.android.callrecorder.CallRecorder;
import com.android.callrecorder.CallRecordingDataStore;
import com.android.callrecorder.CallRecordingPlayer;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.R;
import com.android.services.callrecorder.common.CallRecording;

import java.util.Date;
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
    /** Whether the voicemail controls are shown. */
    private final boolean mShowVoicemail;
    /** Whether the call and SMS controls are shown. */
    private final boolean mShowCallAndSms;
    /** The controls that are shown on top of the history list. */
    private final View mControls;
    /** The listener to changes of focus of the header. */
    private View.OnFocusChangeListener mHeaderFocusChangeListener =
            new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            // When the header is focused, focus the controls above it instead.
            if (hasFocus) {
                mControls.requestFocus();
            }
        }
    };

    private CallRecordingDataStore mCallRecordingDataStore;
    private CallRecordingPlayer mCallRecordingPlayer;

    public CallDetailHistoryAdapter(Context context, LayoutInflater layoutInflater,
            CallTypeHelper callTypeHelper, PhoneCallDetails[] phoneCallDetails,
            boolean showVoicemail, boolean showCallAndSms, View controls,
            CallRecordingDataStore callRecordingDataStore, CallRecordingPlayer callRecordingPlayer) {
        mContext = context;
        mLayoutInflater = layoutInflater;
        mCallTypeHelper = callTypeHelper;
        mPhoneCallDetails = phoneCallDetails;
        mShowVoicemail = showVoicemail;
        mShowCallAndSms = showCallAndSms;
        mControls = controls;
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
            // Voicemail controls are only shown in the main UI if there is a voicemail.
            View voicemailContainer = header.findViewById(R.id.header_voicemail_container);
            voicemailContainer.setVisibility(mShowVoicemail ? View.VISIBLE : View.GONE);
            // Call and SMS controls are only shown in the main UI if there is a known number.
            View callAndSmsContainer = header.findViewById(R.id.header_call_and_sms_container);
            callAndSmsContainer.setVisibility(mShowCallAndSms ? View.VISIBLE : View.GONE);
            header.setFocusable(true);
            header.setOnFocusChangeListener(mHeaderFocusChangeListener);
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
        callTypeIconView.clear();
        callTypeIconView.add(callType);
        callTypeTextView.setText(mCallTypeHelper.getCallTypeText(callType));
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
            durationView.setText(formatDuration(details.duration));
        }

        // do this synchronously to prevent recordings from "popping in" after detail item is displayed
        if (CallRecorder.isEnabled()) {
            mCallRecordingDataStore.open(mContext); // opens unless already open
            List<CallRecording> recordings =
                    mCallRecordingDataStore.getRecordings(details.number.toString(), new Date(details.date));

            ViewGroup playbackView = (ViewGroup) result.findViewById(R.id.recording_playback_layout);
            playbackView.removeAllViews();
            for (CallRecording recording : recordings) {
                PlayButton button = new PlayButton(mContext, recording);
                playbackView.addView(button);
            }
        }

        return result;
    }

    // button to toggle playback for a call recording
    public class PlayButton extends Button implements View.OnClickListener {

        private boolean mPlaying = false;
        private CallRecording mRecording;

        public PlayButton(Context context, CallRecording recording) {
            super(context);
            mRecording = recording;
            reset();
            setBackgroundColor(Color.TRANSPARENT);
            setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (!mPlaying) {
                mCallRecordingPlayer.play(mRecording.fileName, this);
                if (!mCallRecordingPlayer.isPlaying()) {
                    Toast toast = Toast.makeText(mContext, R.string.call_playback_error_message, Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
            else {
                mCallRecordingPlayer.stop();
            }
            mPlaying = mCallRecordingPlayer.isPlaying();
            if (mPlaying) {
                setText(R.string.stop_call_playback);
                setImage(R.drawable.ic_playback_stop_holo_dark);
            }
            else {
                setText(R.string.start_call_playback);
                setImage(R.drawable.ic_playback_holo_dark);
            }
        }

        public void reset() {
            mPlaying = false;
            setText(R.string.start_call_playback);
            setImage(R.drawable.ic_playback_holo_dark);
        }

        private void setImage(int r) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(r, 0, 0, 0);
        }
    }

    private String formatDuration(long elapsedSeconds) {
        long minutes = 0;
        long seconds = 0;

        if (elapsedSeconds >= 60) {
            minutes = elapsedSeconds / 60;
            elapsedSeconds -= minutes * 60;
        }
        seconds = elapsedSeconds;

        return mContext.getString(R.string.callDetailsDurationFormat, minutes, seconds);
    }
}
