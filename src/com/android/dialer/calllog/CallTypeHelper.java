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

import android.content.res.Resources;
import android.provider.CallLog.Calls;

import com.android.dialer.R;

/**
 * Helper class to perform operations related to call types.
 */
public class CallTypeHelper {
    /** Name used to identify incoming calls. */
    private final CharSequence mIncomingName;
    /** Name used to identify outgoing calls. */
    private final CharSequence mOutgoingName;
    /** Name used to identify missed calls. */
    private final CharSequence mMissedName;
    /** Name used to identify voicemail calls. */
    private final CharSequence mVoicemailName;
    /** Color used to identify new missed calls. */
    private final int mNewMissedColor;
    /** Color used to identify new voicemail calls. */
    private final int mNewVoicemailColor;

    //add for csvt call log type
    public static final int INCOMING_CSVT_TYPE = 5;
    public static final int OUTGOING_CSVT_TYPE = 6;
    public static final int MISSED_CSVT_TYPE = 7;

    // Call log type for ims call
    public static final int INCOMING_IMS_TYPE = 21;
    public static final int OUTGOING_IMS_TYPE = 22;
    public static final int MISSED_IMS_TYPE = 23;

    public CallTypeHelper(Resources resources) {
        // Cache these values so that we do not need to look them up each time.
        mIncomingName = resources.getString(R.string.type_incoming);
        mOutgoingName = resources.getString(R.string.type_outgoing);
        mMissedName = resources.getString(R.string.type_missed);
        mVoicemailName = resources.getString(R.string.type_voicemail);
        mNewMissedColor = resources.getColor(R.color.call_log_missed_call_highlight_color);
        mNewVoicemailColor = resources.getColor(R.color.call_log_voicemail_highlight_color);
    }

    /** Returns the text used to represent the given call type. */
    public CharSequence getCallTypeText(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                return mIncomingName;

            case Calls.OUTGOING_TYPE:
                return mOutgoingName;

            case Calls.MISSED_TYPE:
                return mMissedName;

            case Calls.VOICEMAIL_TYPE:
                return mVoicemailName;

            //add for csvt call log type
            case INCOMING_CSVT_TYPE:
                return mIncomingName;

            case OUTGOING_CSVT_TYPE:
                return mOutgoingName;

            case MISSED_CSVT_TYPE:
                return mMissedName;

            //add for csvt call log type
            case INCOMING_IMS_TYPE:
                return mIncomingName;

            case OUTGOING_IMS_TYPE:
                return mOutgoingName;

            case MISSED_IMS_TYPE:
                return mMissedName;
            default:
                return mMissedName;
        }
    }

    /** Returns the color used to highlight the given call type, null if not highlight is needed. */
    public Integer getHighlightedColor(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
            case INCOMING_CSVT_TYPE:
            case INCOMING_IMS_TYPE:
                // New incoming calls are not highlighted.
                return null;

            case Calls.OUTGOING_TYPE:
            case OUTGOING_CSVT_TYPE:
            case OUTGOING_IMS_TYPE:
                // New outgoing calls are not highlighted.
                return null;

            case Calls.MISSED_TYPE:
            case MISSED_CSVT_TYPE:
            case MISSED_IMS_TYPE:
                return mNewMissedColor;

            case Calls.VOICEMAIL_TYPE:
                return mNewVoicemailColor;

            default:
                // Don't highlight calls of unknown types. They are treated as missed calls by
                // the rest of the UI, but since they will never be marked as read by
                // {@link CallLogQueryHandler}, just don't ever highlight them anyway.
                return null;
        }
    }

    public static boolean isMissedCallType(int callType) {
        return (callType != Calls.INCOMING_TYPE && callType != Calls.OUTGOING_TYPE &&
                callType != Calls.VOICEMAIL_TYPE && callType != INCOMING_CSVT_TYPE &&
                callType != OUTGOING_CSVT_TYPE);
    }
}
