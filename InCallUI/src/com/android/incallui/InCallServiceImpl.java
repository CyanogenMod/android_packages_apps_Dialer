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
 * limitations under the License
 */

package com.android.incallui;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telephony.TelephonyManager;

/**
 * Used to receive updates about calls from the Telecom component.  This service is bound to
 * Telecom while there exist calls which potentially require UI. This includes ringing (incoming),
 * dialing (outgoing), and active calls. When the last call is disconnected, Telecom will unbind to
 * the service triggering InCallActivity (via CallList) to finish soon after.
 */
public class InCallServiceImpl extends InCallService {

    static int sPhoneCount = 0;

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        AudioModeProvider.getInstance().onAudioStateChanged(audioState.isMuted(),
                audioState.getRoute(), audioState.getSupportedRouteMask());
    }

    @Override
    public void onBringToForeground(boolean showDialpad) {
        InCallPresenter.getInstance().onBringToForeground(showDialpad);
    }

    @Override
    public void onCallAdded(Call call) {
        InCallPresenter.getInstance().onCallAdded(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        InCallPresenter.getInstance().onCallRemoved(call);
    }

    @Override
    public void onCanAddCallChanged(boolean canAddCall) {
        InCallPresenter.getInstance().onCanAddCallChanged(canAddCall);
    }

    @Override
    public IBinder onBind(Intent intent) {
        final Context context = getApplicationContext();
        sPhoneCount = QtiCallUtils.getPhoneCount(context);
        final ContactInfoCache contactInfoCache = ContactInfoCache.getInstance(context);
        InCallPresenter.getInstance().setUp(
                getApplicationContext(),
                CallList.getInstance(),
                new ExternalCallList(),
                AudioModeProvider.getInstance(),
                new StatusBarNotifier(context, contactInfoCache),
                new ExternalCallNotifier(context, contactInfoCache),
                contactInfoCache,
                new ProximitySensor(
                        context,
                        AudioModeProvider.getInstance(),
                        new AccelerometerListener(context))
                );
        InCallPresenter.getInstance().onServiceBind();
        InCallPresenter.getInstance().maybeStartRevealAnimation(intent);
        TelecomAdapter.getInstance().setInCallService(this);
        CallRecorder.getInstance().setUp(getApplicationContext());

        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);

        InCallPresenter.getInstance().onServiceUnbind();
        tearDown();

        return false;
    }

    private void tearDown() {
        Log.v(this, "tearDown");
        // Tear down the InCall system
        TelecomAdapter.getInstance().clearInCallService();
        InCallPresenter.getInstance().tearDown();
    }
}
