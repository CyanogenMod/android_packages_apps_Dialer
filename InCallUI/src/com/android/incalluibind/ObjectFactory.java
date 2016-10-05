/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.incalluibind;

import android.content.Context;
import android.content.Intent;

import com.android.dialer.lookup.ReverseLookupService;
import com.android.incallui.CallCardPresenter.EmergencyCallListener;
import com.android.incallui.ContactUtils;
import com.android.incallui.DistanceHelper;
import com.android.incallui.service.PhoneNumberService;

public class ObjectFactory {

    public static PhoneNumberService newPhoneNumberService(Context context) {
        return new ReverseLookupService(context);
    }

    public static EmergencyCallListener newEmergencyCallListener() {
        return null;
    }

    /** @return An {@link Intent} to be broadcast when the InCallUI is visible. */
    public static Intent getUiReadyBroadcastIntent(Context context) {
        return null;
    }

    /**
     * @return An {@link Intent} to be broadcast when the call state button in the InCallUI is
     * touched while in a call.
     */
    public static Intent getCallStateButtonBroadcastIntent(Context context) {
        return null;
    }

    public static DistanceHelper newDistanceHelper(Context context,
            DistanceHelper.Listener listener) {
        return null;
    }

    public static ContactUtils getContactUtilsInstance(Context context) {
        return null;
    }
}
