/*
 * Copyright (C) 2013-2014, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
 * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
          copyright notice, this list of conditions and the following
          disclaimer in the documentation and/or other materials provided
          with the distribution.
 * Neither the name of The Linux Foundation, Inc. nor the names of its
          contributors may be used to endorse or promote products derived
          from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.dialer.calllog;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.contacts.common.MoreContactUtils;
import com.android.dialer.R;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * Displays a list of call log entries.
 */
public class MSimCallLogFragment extends CallLogFragment {
    private static final String TAG = "MSimCallLogFragment";

    /**
     * Key for the call log sub saved in the default preference.
     */
    private static final String PREFERENCE_KEY_CALLLOG_SUB = "call_log_sub";

    // Add and change for filter call log.
    private Spinner mFilterSubSpinnerView;
    private Spinner mFilterStatusSpinnerView;

    // Default to all slots.
    private int mCallSubFilter = CallLogQueryHandler.CALL_SUB_ALL;

    private OnItemSelectedListener mSubSelectedListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Log.i(TAG, "Sub selected, position: " + position);
            int sub = position - 1;
            mCallSubFilter = sub;
            setSelectedSub(sub);
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, 0, mCallSubFilter);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do nothing.
        }

    };

    private OnItemSelectedListener mStatusSelectedListener = new OnItemSelectedListener() {

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            Log.i(TAG, "Status selected, position: " + position);
            mCallTypeFilter = ((SpinnerContent)parent.getItemAtPosition(position)).value;
            mCallLogQueryHandler.fetchCalls(mCallTypeFilter, 0, mCallSubFilter);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Do nothing.
        }

    };

    @Override
    protected void setVoicemailSourcesAvailable(boolean voicemailSourcesAvailable) {
        if (mVoicemailSourcesAvailable == voicemailSourcesAvailable) return;
        mVoicemailSourcesAvailable = voicemailSourcesAvailable;

        Activity activity = getActivity();
        if (activity != null) {
            // This is so that the options menu content is updated.
            activity.invalidateOptionsMenu();
            updateFilterSpinnerViews();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.msim_call_log_fragment, container, false);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);

        mFilterSubSpinnerView = (Spinner) view.findViewById(R.id.filter_sub_spinner);
        mFilterStatusSpinnerView = (Spinner) view.findViewById(R.id.filter_status_spinner);

        // Update the filter views.
        updateFilterSpinnerViews();

        return view;
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, 0, mCallSubFilter);
    }

    @Override
    public void startCallsQuery() {
        mAdapter.setLoading(true);
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, 0, mCallSubFilter);
    }

    /**
     * Initialize the filter views content.
     */
    private void updateFilterSpinnerViews() {
        if (mFilterSubSpinnerView == null
                || mFilterStatusSpinnerView == null) {
            Log.w(TAG, "The filter spinner view is null!");
            return;
        }

        final TelephonyManager telephony = (TelephonyManager) getActivity().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (!telephony.isMultiSimEnabled()) {
            mFilterSubSpinnerView.setVisibility(View.GONE);
        } else {
            // Update the sub filter's content.
            mCallSubFilter = getSelectedSub();
            ArrayAdapter<SpinnerContent> filterSubAdapter = new ArrayAdapter<SpinnerContent>(
                    this.getActivity(), R.layout.call_log_spinner_item,
                    SpinnerContent.setupSubFilterContent(getActivity()));
            mFilterSubSpinnerView.setAdapter(filterSubAdapter);
            mFilterSubSpinnerView.setOnItemSelectedListener(mSubSelectedListener);
            SpinnerContent.setSpinnerContentValue(mFilterSubSpinnerView, mCallSubFilter);
        }

        // Update the status filter's content.
        ArrayAdapter<SpinnerContent> filterStatusAdapter = new ArrayAdapter<SpinnerContent>(
                this.getActivity(), R.layout.call_log_spinner_item,
                SpinnerContent.setupStatusFilterContent(getActivity(), mVoicemailSourcesAvailable));
        mFilterStatusSpinnerView.setAdapter(filterStatusAdapter);
        mFilterStatusSpinnerView.setOnItemSelectedListener(mStatusSelectedListener);
        SpinnerContent.setSpinnerContentValue(mFilterStatusSpinnerView, mCallTypeFilter);
    }

    /**
     * @return the saved selected subscription.
     */
    private int getSelectedSub() {
        // Get the saved selected sub, and the default value is display all.
        int sub = PreferenceManager.getDefaultSharedPreferences(this.getActivity()).getInt(
                PREFERENCE_KEY_CALLLOG_SUB, CallLogQueryHandler.CALL_SUB_ALL);
        return sub;
    }

    /**
     * Save the selected subscription to preference.
     */
    private void setSelectedSub(int sub) {
        // Save the selected sub to the default preference.
        PreferenceManager.getDefaultSharedPreferences(this.getActivity()).edit()
                .putInt(PREFERENCE_KEY_CALLLOG_SUB, sub).commit();
    }
}
