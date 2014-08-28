/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.dialer;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.internal.telephony.TelephonyCapabilities;
import com.android.internal.telephony.TelephonyIntents;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";
    private static final String PRL_VERSION_DISPLAY = "*#0000#";

    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     *
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     *
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        return handleChars(context, input, false, textField);
    }

    static boolean handleChars(Context context, String input) {
        return handleChars(context, input, false, null);
    }

    static boolean handleChars(Context context, String input, boolean useSystemWindow,
            EditText textField) {

        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (handlePRLVersion(context, dialString)
                || handleIMEIDisplay(context, dialString, useSystemWindow)
                || handleRegulatoryInfoDisplay(context, dialString)
                || handlePinEntry(context, dialString)
                || handleAdnEntry(context, dialString, textField)
                || handleSecretCode(context, dialString)) {
            return true;
        }

        return false;
    }

    static private boolean handlePRLVersion(Context context, String input) {
        if (input.equals(PRL_VERSION_DISPLAY)) {
            try {
                Intent intent = new Intent("android.intent.action.ENGINEER_MODE_DEVICEINFO");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "no activity to handle showing device info");
            }
        }
        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     *
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     *
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
            Intent intent = new Intent(TelephonyIntents.SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /**
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* ADN entries are of the form "N(N)(N)#" */

        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null
                || !TelephonyCapabilities.supportsAdn(telephonyManager.getCurrentPhoneType())) {
            return false;
        }

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        Uri uri = null;

        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                SimContactQueryCookie sc = new SimContactQueryCookie(index - 1, handler,
                        ADN_QUERY_TOKEN);

                // setup the cookie fields
                sc.contactNum = index - 1;
                sc.setTextField(textField);

                // create the progress dialog
                sc.progressDialog = new ProgressDialog(context);
                sc.progressDialog.setTitle(R.string.simContacts_title);
                sc.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                sc.progressDialog.setIndeterminate(true);
                sc.progressDialog.setCancelable(true);
                sc.progressDialog.setOnCancelListener(sc);
                sc.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                // display the progress dialog
                sc.progressDialog.show();

                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    int subscription = MSimTelephonyManager.getDefault().
                            getPreferredVoiceSubscription();
                    String[] adn = {"adn", "adn_sub2", "adn_sub3"};

                    uri = Uri.parse("content://iccmsim/" + adn[subscription]);
                } else {
                    uri = Uri.parse("content://icc/adn");
                }

                // run the query.
                handler.startQuery(ADN_QUERY_TOKEN, sc, uri,
                        new String[]{ADN_PHONE_NUMBER_COLUMN_NAME}, null, null, null);

                if (sPreviousAdnQueryHandler != null) {
                    // It is harmless to call cancel() even after the handler's gone.
                    sPreviousAdnQueryHandler.cancel();
                }
                sPreviousAdnQueryHandler = handler;
                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    static boolean handlePinEntry(Context context, String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            try {
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    int subscription = 0;

                    // On multisim targets handle PIN/PUK related MMI commands on
                    // Voice preferred subscription.
                    subscription = MSimTelephonyManager.getDefault()
                            .getPreferredVoiceSubscription();
                    return ITelephonyMSim.Stub.asInterface(ServiceManager.getService("phone_msim"))
                            .handlePinMmi(input, subscription);
                } else {
                    return ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                            .handlePinMmi(input);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to handlePinMmi due to remote exception");
                return false;
            }
        }
        return false;
    }

    static boolean handleIMEIDisplay(Context context, String input, boolean useSystemWindow) {
        if (input.equals(MMI_IMEI_DISPLAY)) {
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                return handleMSimIMEIDisplay(context);
            }
            int phoneType;
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                int subscription = MSimTelephonyManager.getDefault().
                        getPreferredVoiceSubscription();

                phoneType = ((MSimTelephonyManager)context.getSystemService(
                        Context.MSIM_TELEPHONY_SERVICE)).getCurrentPhoneType(subscription);
            } else {
                phoneType = ((TelephonyManager)context.getSystemService(
                        Context.TELEPHONY_SERVICE)).getCurrentPhoneType();
            }

            if (phoneType == TelephonyManager.PHONE_TYPE_GSM) {
                showIMEIPanel(context, useSystemWindow);
                return true;
            } else if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                showMEIDPanel(context, useSystemWindow);
                return true;
            }
        }

        return false;
    }

    static boolean handleMSimIMEIDisplay(Context context) {
        StringBuffer deviceIds = new StringBuffer();
        int titleId = R.string.device_id;
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            if (i != 0) {
                deviceIds.append("\n");
            }
            int phoneType = MSimTelephonyManager.getDefault().getCurrentPhoneType(i);
            if (phoneType != TelephonyManager.PHONE_TYPE_GSM
                    && phoneType != TelephonyManager.PHONE_TYPE_CDMA) {
                return false;
            }
            deviceIds.append(context
                    .getString(PhoneConstants.PHONE_TYPE_CDMA == phoneType ? R.string.meid
                            : R.string.imei)
                    + " ");
            deviceIds.append(MSimTelephonyManager.getDefault().getDeviceId(i));
        }
        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(titleId)
                .setMessage(deviceIds.toString())
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
        return true;
    }

    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            ComponentName regInfoDisplayActivity = new ComponentName(
                    "com.android.settings", "com.android.settings.RegulatoryInfoDisplayActivity");
            Intent showRegInfoIntent = new Intent("android.settings.SHOW_REGULATORY_INFO");
            showRegInfoIntent.setComponent(regInfoDisplayActivity);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }

    // TODO: Combine showIMEIPanel() and showMEIDPanel() into a single
    // generic "showDeviceIdPanel()" method, like in the apps/Phone
    // version of SpecialCharSequenceMgr.java.  (This will require moving
    // the phone app's TelephonyCapabilities.getDeviceIdLabel() method
    // into the telephony framework, though.)
    private static void showIMEIPanel(Context context, boolean useSystemWindow) {
        String imeiStr;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            imeiStr = getMSimDeviceIds(context);
        } else {
            imeiStr = ((TelephonyManager)context.
                    getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        }

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.imei)
                .setMessage(imeiStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
    }

    private static void showMEIDPanel(Context context, boolean useSystemWindow) {
        String meidStr;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            meidStr = getMSimDeviceIds(context);
        } else {
            meidStr = ((TelephonyManager)context.
                    getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        }

        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(R.string.meid)
                .setMessage(meidStr)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show();
    }

    private static String getMSimDeviceIds(Context context) {
        // Show all IMEI/MEIDs for Multi-SIM
        int numPhones = MSimTelephonyManager.getDefault().getPhoneCount();

        MSimTelephonyManager telephony = (MSimTelephonyManager)context.
                getSystemService(Context.MSIM_TELEPHONY_SERVICE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numPhones; i++) {
            if (i != 0) {
                sb.append("\n");
            }
            sb.append(telephony.getDeviceId(i));
        }
        return sb.toString();
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            sPreviousAdnQueryHandler = null;
            if (mCanceled) {
                return;
            }

            SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

            // close the progress dialog.
            sc.progressDialog.dismiss();

            // get the EditText to update or see if the request was cancelled.
            EditText text = sc.getTextField();

            // if the textview is valid, and the cursor is valid and postionable
            // on the Nth number, then we update the text field and display a
            // toast indicating the caller name.
            if ((c != null) && (text != null) && (c.moveToPosition(sc.contactNum))) {
                String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                String number = c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                // fill the text in.
                text.getText().replace(0, 0, number);

                // display the name as a toast
                Context context = sc.progressDialog.getContext();
                name = context.getString(R.string.menu_callNumber, name);
                Toast.makeText(context, name, Toast.LENGTH_SHORT)
                    .show();
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fails when the
            // query already started.
            cancelOperation(ADN_QUERY_TOKEN);
        }
    }
}
