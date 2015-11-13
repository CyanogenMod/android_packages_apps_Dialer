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
    /** Name used to identify incoming video calls. */
    private final CharSequence mIncomingVideoName;
    /** Name used to identify outgoing video calls. */
    private final CharSequence mOutgoingVideoName;
    /** Name used to identify missed video calls. */
    private final CharSequence mMissedVideoName;
    /** Name used to identify voicemail calls. */
    private final CharSequence mVoicemailName;
    /** Name used to identify blacklisted calls */
    private final CharSequence mBlacklistedName;
    /** Color used to identify new missed calls. */
    private final int mNewMissedColor;
    /** Color used to identify new voicemail calls. */
    private final int mNewVoicemailColor;

    public CallTypeHelper(Resources resources) {
        // Cache these values so that we do not need to look them up each time.
        mIncomingName = resources.getString(R.string.type_incoming);
        mOutgoingName = resources.getString(R.string.type_outgoing);
        mMissedName = resources.getString(R.string.type_missed);
        mIncomingVideoName = resources.getString(R.string.type_incoming_video);
        mOutgoingVideoName = resources.getString(R.string.type_outgoing_video);
        mMissedVideoName = resources.getString(R.string.type_missed_video);
        mVoicemailName = resources.getString(R.string.type_voicemail);
        mBlacklistedName = resources.getText(R.string.type_blacklist);
        mNewMissedColor = resources.getColor(R.color.call_log_missed_call_highlight_color);
        mNewVoicemailColor = resources.getColor(R.color.call_log_voicemail_highlight_color);
    }

    /** Returns the text used to represent the given call type. */
    public CharSequence getCallTypeText(int callType, boolean isVideoCall) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                if (isVideoCall) {
                    return mIncomingVideoName;
                } else {
                    return mIncomingName;
                }

            case Calls.OUTGOING_TYPE:
                if (isVideoCall) {
                    return mOutgoingVideoName;
                } else {
                    return mOutgoingName;
                }

            case Calls.MISSED_TYPE:
                if (isVideoCall) {
                    return mMissedVideoName;
                } else {
                    return mMissedName;
                }

            case Calls.VOICEMAIL_TYPE:
                return mVoicemailName;

            case Calls.BLACKLIST_TYPE:
                return mBlacklistedName;

            default:
                return mMissedName;
        }
    }

    /** Returns the color used to highlight the given call type, null if not highlight is needed. */
    public Integer getHighlightedColor(int callType) {
        switch (callType) {
            case Calls.INCOMING_TYPE:
                // New incoming calls are not highlighted.
                return null;

            case Calls.OUTGOING_TYPE:
                // New outgoing calls are not highlighted.
                return null;

            case Calls.MISSED_TYPE:
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
                callType != Calls.VOICEMAIL_TYPE);
    }
}
