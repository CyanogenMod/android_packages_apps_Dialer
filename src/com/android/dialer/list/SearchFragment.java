/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.dialer.list;

import static android.Manifest.permission.CALL_PHONE;
import static android.Manifest.permission.READ_CONTACTS;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Space;

import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactListItemView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.list.PhoneNumberPickerFragment;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.dialpad.DialpadFragment.ErrorDialogFragment;
import com.android.dialer.R;
import com.android.dialer.incall.InCallMetricsHelper;
import com.android.dialer.util.CoachMarkDrawableHelper;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.IntentUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.phone.common.animation.AnimUtils;
import com.android.phone.common.incall.CallMethodInfo;
import com.android.phone.common.incall.CreditBarHelper;

import com.android.phone.common.incall.DialerDataSubscription;
import com.android.phone.common.incall.utils.CallMethodFilters;
import com.cyanogen.ambient.incall.extension.OriginCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class SearchFragment extends PhoneNumberPickerFragment
        implements DialerPhoneNumberListAdapter.searchMethodClicked,
        CreditBarHelper.CreditBarVisibilityListener,
        EmptyContentView.OnEmptyViewActionButtonClickedListener {
    private static final String TAG  = SearchFragment.class.getSimpleName();

    private OnListFragmentScrolledListener mActivityScrollListener;
    private View.OnTouchListener mActivityOnTouchListener;

    /*
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
    private int mShadowHeight;
    private int mPaddingTop;
    private int mShowDialpadDuration;
    private int mHideDialpadDuration;

    /**
     * Used to resize the list view containing search results so that it fits the available space
     * above the dialpad. Does not have a user-visible effect in regular touch usage (since the
     * dialpad hides that portion of the ListView anyway), but improves usability in accessibility
     * mode.
     */
    private Space mSpacer;

    private HostInterface mActivity;

    protected EmptyContentView mEmptyView;

    public CallMethodInfo mCurrentCallMethodInfo;

    public HashMap<ComponentName, CallMethodInfo> mAvailableProviders;

    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1;

    @Override
    public void creditsBarVisibilityChanged(int visibility) {
        DialtactsActivity da = (DialtactsActivity) getActivity();
        da.moveFabInSearchUI();
    }

    @Override
    public void onEmptyViewActionButtonClicked() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        requestPermissions(new String[]{CALL_PHONE}, CALL_PHONE_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == CALL_PHONE_PERMISSION_REQUEST_CODE) {
            setupEmptyView();
        }
    }

    public interface HostInterface {
        public boolean isActionBarShowing();
        public boolean isDialpadShown();
        public int getDialpadHeight();
        public int getActionBarHideOffset();
        public int getActionBarHeight();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isSearchMode()) {
            getAdapter().setHasHeader(0, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = mActivity.getActionBarHeight();
        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
        mShowDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
        mHideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

        final View parentView = getView();

        final ListView listView = getListView();

        if (mEmptyView == null) {
            mEmptyView = new EmptyContentView(getActivity());
            ((ViewGroup) getListView().getParent()).addView(mEmptyView);
            getListView().setEmptyView(mEmptyView);
            setupEmptyView();
        }

        listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
            }
        });
        if (mActivityOnTouchListener != null) {
            listView.setOnTouchListener(mActivityOnTouchListener);
        }

        updateCoachMarkDrawable();

        updatePosition(false /* animate */);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        Animator animator = null;
        if (nextAnim != 0) {
            animator = AnimatorInflater.loadAnimator(getActivity(), nextAnim);
        }
        if (animator != null) {
            final View view = getView();
            final int oldLayerType = view.getLayerType();
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setLayerType(oldLayerType, null);
                }
            });
        }
        return animator;
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.setHasHeader(0, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    public void updateCoachMarkDrawable() {
        DialtactsActivity da = (DialtactsActivity) mActivity;
        if (da != null && da.isInSearchUi()) {

            String unFormattedString = getString(R.string.provider_search_help);
            CoachMarkDrawableHelper.assignViewTreeObserverWithHeight(getView(), da,
                    da.getSearchTextLayout(), mActivity.getActionBarHeight(), true,
                    da.getSearchEditText(), unFormattedString, 0.8f);
        }
    }

    /**
     * Return true if phone number is prohibited by a value -
     * (R.string.config_prohibited_phone_number_regexp) in the config files. False otherwise.
     */
    public boolean checkForProhibitedPhoneNumber(String number) {
        // Regular expression prohibiting manual phone call. Can be empty i.e. "no rule".
        String prohibitedPhoneNumberRegexp = getResources().getString(
            R.string.config_prohibited_phone_number_regexp);

        // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
        // test equipment.
        if (number != null
                && !TextUtils.isEmpty(prohibitedPhoneNumberRegexp)
                && number.matches(prohibitedPhoneNumberRegexp)) {
            Log.d(TAG, "The phone number is prohibited explicitly by a rule.");
            if (getActivity() != null) {
                DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                        R.string.dialog_phone_call_prohibited_message);
                dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
            }

            return true;
        }
        return false;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        adapter.setSearchListner(this);
        adapter.setAvailableCallMethods(CallMethodFilters.getAllEnabledCallMethods(
                DialerDataSubscription.get(getActivity())));
        return adapter;
    }

    @Override
    public void onPause() {
        super.onPause();

        DialtactsActivity da = (DialtactsActivity) getActivity();
        if (da != null) {
            CreditBarHelper.clearCallRateInformation(da.getGlobalCreditsBar(), this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final DialtactsActivity da = (DialtactsActivity) getActivity();
        if (mCurrentCallMethodInfo == null && da != null && da.isInSearchUi()) {
            setCurrentCallMethod(da.getCurrentCallMethod());
        } else {
            updateCallCreditInfo();
        }
    }

    @Override
    public void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position, false);

        final OnPhoneNumberPickerActionListener listener;
        final Intent intent;
        final String number;
        CallMethodInfo currentCallMethod = getCurrentCallMethod();

        Log.i(TAG, "onItemClick: shortcutType=" + shortcutType);

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                number = adapter.getQueryString();
                if (currentCallMethod != null && currentCallMethod.mIsInCallProvider &&
                        !PhoneNumberUtils.isEmergencyNumber(number)) {
                    onProviderClick(position, currentCallMethod);
                } else {
                    super.onItemClick(position, id);
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
                number = adapter.getQueryString();
                if (currentCallMethod != null && currentCallMethod.mIsInCallProvider &&
                        !PhoneNumberUtils.isEmergencyNumber(number)) {
                    placePSTNCall(number, currentCallMethod);
                } else {
                    listener = getOnPhoneNumberPickerListener();
                    if (listener != null && !checkForProhibitedPhoneNumber(number)) {
                        listener.onCallNumberDirectly(number, false, adapter.getMimeType(position));
                    }
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_CREATE_NEW_CONTACT:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                intent = IntentUtil.getNewContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_EXISTING_CONTACT:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                intent = IntentUtil.getAddToExistingContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_SEND_SMS_MESSAGE:
                number = adapter.getFormattedQueryString();
                intent = IntentUtil.getSendSmsIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent);
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
                number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getQueryString() : mAddToContactNumber;
                listener = getOnPhoneNumberPickerListener();
                if (listener != null && !checkForProhibitedPhoneNumber(number)) {
                    listener.onCallNumberDirectly(number, true /* isVideoCall */,
                            adapter.getMimeType(position));
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_PROVIDER_ACTION:
                int truePosition = adapter.getShortcutTypeFromPosition(position, true);
                int index = DialerPhoneNumberListAdapter.SHORTCUT_COUNT - truePosition - 1;
                number = adapter.getQueryString();
                if (!PhoneNumberUtils.isEmergencyNumber(number)) {
                    CallMethodInfo cmi = adapter.getProviders().get(index);
                    cmi.placeCall(OriginCodes.DIALPAD_T9_SEARCH, number, getContext(), false, true);
                } else {
                    listener = getOnPhoneNumberPickerListener();
                    if (listener != null && !checkForProhibitedPhoneNumber(number)) {
                        listener.onCallNumberDirectly(number);
                    }
                }
                break;
        }
    }

    private void placePSTNCall(String number, CallMethodInfo cmi) {
        cmi.placeCall(OriginCodes.DIALPAD_T9_SEARCH, number, getContext(), false, true);
    }

    protected void onProviderClick(int position, CallMethodInfo cmi) {
        cmi.placeCall(OriginCodes.DIALPAD_T9_SEARCH, getPhoneNumber(position), getContext(), false,
                false, getPhoneNumberMimeType(position));
    }

    public void setCurrentCallMethod(CallMethodInfo cmi) {
        if (cmi != null && !cmi.equals(mCurrentCallMethodInfo)) {
            mCurrentCallMethodInfo = cmi;
            final DialerPhoneNumberListAdapter adapter
                    = (DialerPhoneNumberListAdapter) getAdapter();
            if (adapter != null) {
                adapter.setCurrentCallMethod(cmi);
            }
            setupEmptyView();
            setAdditionalMimeTypeSearch(cmi.mMimeType);
            reloadData();
        }
        updateCallCreditInfo();
    }

    public void updateCallCreditInfo() {
        DialtactsActivity da = (DialtactsActivity) getActivity();
        if (da != null) {
            CallMethodInfo cmi = getCurrentCallMethod();
            if (cmi != null && cmi.mIsInCallProvider && !da.isDialpadShown()) {
                CreditBarHelper.callMethodCredits(da.getGlobalCreditsBar(), cmi, getResources(), this);
            } else {
                CreditBarHelper.clearCallRateInformation(da.getGlobalCreditsBar(), this);
            }
            if (cmi != null) {
                final DialerPhoneNumberListAdapter adapter
                        = (DialerPhoneNumberListAdapter) getAdapter();
                if (adapter != null) {
                    adapter.setCurrentCallMethod(cmi);
                }
            }
        }
    }

    public CallMethodInfo getCurrentCallMethod() {
        return mCurrentCallMethodInfo;
    }

    /**
     * Updates the position and padding of the search fragment, depending on whether the dialpad is
     * shown. This can be optionally animated.
     * @param animate
     */
    public void updatePosition(boolean animate) {
        // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
        int startTranslationValue =
                mActivity.isDialpadShown() ? mActionBarHeight - mShadowHeight: -mShadowHeight;
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (animate || mActivity.isActionBarShowing()) {
            endTranslationValue =
                    mActivity.isDialpadShown() ? 0 : mActionBarHeight -mShadowHeight;
        }
        if (animate) {
            // If the dialpad will be shown, then this animation involves sliding the list up.
            final boolean slideUp = mActivity.isDialpadShown();

            Interpolator interpolator = slideUp ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT ;
            int duration = slideUp ? mShowDialpadDuration : mHideDialpadDuration;
            getView().setTranslationY(startTranslationValue);
            getView().animate()
                    .translationY(endTranslationValue)
                    .setInterpolator(interpolator)
                    .setDuration(duration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (!slideUp) {
                                resizeListView();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (slideUp) {
                                resizeListView();
                            }
                        }
                    });

        } else {
            getView().setTranslationY(endTranslationValue);
            resizeListView();
        }

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        listView.setPaddingRelative(
                listView.getPaddingStart(),
                paddingTop,
                listView.getPaddingEnd(),
                listView.getPaddingBottom());
    }

    public void resizeListView() {
        if (mSpacer == null) {
            return;
        }
        int spacerHeight = mActivity.isDialpadShown() ? mActivity.getDialpadHeight() : 0;
        if (spacerHeight != mSpacer.getHeight()) {
            final LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mSpacer.getLayoutParams();
            lp.height = spacerHeight;
            mSpacer.setLayoutParams(lp);
        }
    }

    @Override
    protected void startLoading() {
        if (isAdded() && PermissionsUtil.hasPermission(getActivity(), READ_CONTACTS)) {
            super.startLoading();
        } else if (TextUtils.isEmpty(getQueryString())) {
            // Clear out any existing call shortcuts.
            final DialerPhoneNumberListAdapter adapter =
                    (DialerPhoneNumberListAdapter) getAdapter();
            adapter.disableAllShortcuts();
        } else {
            // The contact list is not going to change (we have no results since permissions are
            // denied), but the shortcuts might because of the different query, so update the
            // list.
            getAdapter().notifyDataSetChanged();
        }

        setupEmptyView();
    }

    public void setOnTouchListener(View.OnTouchListener onTouchListener) {
        mActivityOnTouchListener = onTouchListener;
    }

    LayoutInflater mInflateView;

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        mInflateView = inflater;
        final LinearLayout parent = (LinearLayout) super.inflateView(inflater, container);
        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mSpacer = new Space(getActivity());
            parent.addView(mSpacer,
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
        }
        return parent;
    }

    public void setupEmptyView() {
        DialtactsActivity dialActivity = (DialtactsActivity) mActivity;
        if (mEmptyView != null && dialActivity != null && isAdded()) {
            ContactEntryListAdapter adapter = getAdapter();
            Resources r = getResources();
            mEmptyView.setWidth(dialActivity.getDialpadWidth());
            if (!PermissionsUtil.hasPermission(getActivity(), CALL_PHONE)) {
                mEmptyView.setImage(R.drawable.empty_contacts);
                mEmptyView.setActionLabel(R.string.permission_single_turn_on);
                mEmptyView.setDescription(R.string.cm_permission_place_call);
                mEmptyView.setSubMessage(null);
                mEmptyView.setActionClickedListener(this);
            } else if (adapter.getCount() == 0 && mActivity.isDialpadShown()) {
                mEmptyView.setActionLabel(mEmptyView.NO_LABEL);
                mEmptyView.setImage(null);

                // Get Current InCall plugin specific call methods, we don't want to update this
                // suddenly so just the currently available ones are fine.
                if (mAvailableProviders == null) {
                    mAvailableProviders = new HashMap<ComponentName, CallMethodInfo>();
                    mAvailableProviders.putAll(CallMethodFilters.getAllEnabledCallMethods(
                            DialerDataSubscription.get(getActivity())));
                }

                if (mCurrentCallMethodInfo == null) {
                    mCurrentCallMethodInfo = dialActivity.getCurrentCallMethod();
                }

                if (mCurrentCallMethodInfo != null && mCurrentCallMethodInfo.mIsInCallProvider &&
                        mCurrentCallMethodInfo.mSingleColorBrandIcon != null) {
                    showProviderHint(r);
                } else {
                    showSuggestion(r);
                }
            }
        }
    }

    public void showNormalT9Hint(Resources r) {
        mEmptyView.setImage(null);
        mEmptyView.setDescription(R.string.empty_dialpad_t9_example);
        mEmptyView.setSubMessage(R.string.empty_dialpad_t9_example_subtext);
    }

    public void showProviderHint(Resources r) {
        String text;
        if (!mCurrentCallMethodInfo.mIsAuthenticated) {
            // Sign into current selected call method to make calls
            text = getString(R.string.sign_in_hint_text, mCurrentCallMethodInfo.mName);
        } else {
            // InCallApi provider specified hint
            text = mCurrentCallMethodInfo.getHintText();
        }
        if (TextUtils.isEmpty(text)) {
            showNormalT9Hint(r);
        } else {
            Drawable heroImage = mCurrentCallMethodInfo.mSingleColorBrandIcon;
            heroImage.setTint(r.getColor(R.color.hint_image_color));

            int orientation = r.getConfiguration().orientation;
            mEmptyView.setImage(heroImage, orientation == Configuration.ORIENTATION_PORTRAIT);
            mEmptyView.setDescription(text);
            mEmptyView.setSubMessage(null);
            // TODO: put action button for login in or switching provider!
        }
    }

    public void showSuggestion(Resources r) {
        ConnectivityManager connManager =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        CallMethodInfo emergencyOnlyCallMethod
                = CallMethodInfo.getEmergencyCallMethod(getContext());

        if (mCurrentCallMethodInfo != null) {
            int orientation = r.getConfiguration().orientation;
            if (mCurrentCallMethodInfo.equals(emergencyOnlyCallMethod)) {
                // If no sims available and emergency only call method selected,
                // alert user that only emergency calls are allowed for the current call method.
                String text = r.getString(R.string.emergency_call_hint_text);
                Drawable heroImage = r.getDrawable(R.drawable.ic_nosim);
                heroImage.setTint(r.getColor(R.color.emergency_call_icon_color));

                mEmptyView.setImage(heroImage, orientation == Configuration.ORIENTATION_PORTRAIT);
                mEmptyView.setDescription(text);
                mEmptyView.setSubMessage(null);
            } else if (!mAvailableProviders.isEmpty() &&
                    !mCurrentCallMethodInfo.mIsInCallProvider &&
                    mWifi.isConnected()) {
                TelephonyManager tm = (TelephonyManager) getActivity()
                                .getSystemService(Context.TELEPHONY_SERVICE);
                String template;
                Drawable heroImage;
                String text;

                InCallMetricsHelper.Events event = null;
                CallMethodInfo hintTextMethod = hintTextRequest();
                if (TextUtils.isEmpty(tm.getNetworkOperator())) {
                    heroImage = r.getDrawable(R.drawable.ic_signal_wifi_3_bar);
                    template = r.getString(R.string.wifi_hint_text);
                    text = String.format(template, hintTextMethod.mName);
                    event = InCallMetricsHelper.Events.INAPP_NUDGE_DIALER_WIFI;
                } else if (tm.isNetworkRoaming(mCurrentCallMethodInfo.mSubId)) {
                    heroImage = r.getDrawable(R.drawable.ic_roaming);
                    template = r.getString(R.string.roaming_hint_text);
                    text = String.format(template, mCurrentCallMethodInfo.mName,
                            hintTextMethod.mName);
                    event = InCallMetricsHelper.Events.INAPP_NUDGE_DIALER_ROAMING;
                } else {
                    showNormalT9Hint(r);
                    return;
                }

                mEmptyView.setImage(heroImage, orientation == Configuration.ORIENTATION_PORTRAIT);
                mEmptyView.setDescription(text);
                mEmptyView.setSubMessage(null);

                InCallMetricsHelper.increaseCountOfMetric(
                        hintTextMethod.mComponent, event,
                        InCallMetricsHelper.Categories.INAPP_NUDGES,
                        InCallMetricsHelper.Parameters.COUNT);
            } else {
                showNormalT9Hint(r);
            }
        } else {
            showNormalT9Hint(r);
        }
    }

    private CallMethodInfo hintTextRequest() {
        // Randomly choose an item that is not a sim to prompt user to switch to
        List<CallMethodInfo> valuesList =
                new ArrayList<CallMethodInfo>(mAvailableProviders.values());

        int randomIndex = new Random().nextInt(valuesList.size());
        return valuesList.get(randomIndex);
    }
}
