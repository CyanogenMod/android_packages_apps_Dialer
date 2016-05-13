package com.android.dialer.tests.nudges;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import android.widget.ToggleButton;

import com.android.dialer.discovery.DiscoveryEventHandler;
import com.android.dialer.tests.R;


import com.cyanogen.ambient.discovery.util.NudgeKey;


/**
 * InCallMod Metrics Test Activity
 */
public class InCallModTestActivity extends Activity {

    private Button mUC1Button;
    private Button mUC2Button;
    private Button mUC3Button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.in_call_mod_test);

        mUC1Button = (Button) findViewById(R.id.ucone);
        mUC1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new DiscoveryEventHandler(getApplicationContext())
                        .getNudgeProvidersWithKey(NudgeKey.NOTIFICATION_ROAMING, true);

            }
        });

        mUC2Button = (Button) findViewById(R.id.uctwo);
        mUC2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new DiscoveryEventHandler(getApplicationContext())
                        .getNudgeProvidersWithKey(NudgeKey.NOTIFICATION_INTERNATIONAL_CALL, true);

            }
        });

        mUC3Button = (Button) findViewById(R.id.ucthree);
        mUC3Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                new DiscoveryEventHandler(getApplicationContext())
                        .getNudgeProvidersWithKey(NudgeKey.NOTIFICATION_WIFI_CALL, true);

            }
        });
    }
}
