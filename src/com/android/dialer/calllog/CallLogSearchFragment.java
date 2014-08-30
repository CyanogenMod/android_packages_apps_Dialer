/*
 * Copyright (C) 2014, The Linux Foundation. All rights reserved.

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

import android.app.ListFragment;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.CallLog.Calls;

import com.android.contacts.common.GeoUtil;
import com.android.dialer.R;
import com.android.dialerbind.ObjectFactory;


public class CallLogSearchFragment extends CallLogFragment {

    private String mQueryString;

    private void updateCallList(int filterType) {
        mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL);
    }

    public void fetchCalls() {
        if (TextUtils.isEmpty(mQueryString)) {
            mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL);
        } else {
            mCallLogQueryHandler.fetchCalls(mQueryString);
        }
    }

    public void startCallsQuery() {
        mAdapter.setLoading(true);
        if (TextUtils.isEmpty(mQueryString)) {
            mCallLogQueryHandler.fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL);
        } else {
            mCallLogQueryHandler.fetchCalls(mQueryString);
        }
    }

    public void setQueryString(String queryString) {
        if (!TextUtils.equals(mQueryString, queryString)) {
            mQueryString = queryString;
            if (mAdapter != null) {
                mAdapter.setLoading(true);
                mAdapter.setQueryString(mQueryString);
                if (TextUtils.isEmpty(queryString)) {
                    mCallLogQueryHandler
                            .fetchCalls(CallLogQueryHandler.CALL_TYPE_ALL);
                } else {
                    mCallLogQueryHandler.fetchCalls(queryString);
                }
            }
        }
    }

}
