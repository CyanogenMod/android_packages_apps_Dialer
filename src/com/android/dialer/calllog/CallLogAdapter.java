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

import android.accounts.Account;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.PhoneLookup;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.widget.GroupingListAdapter;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.DialtactsActivity;
import com.android.dialer.PhoneCallDetails;
import com.android.dialer.PhoneCallDetailsHelper;
import com.android.dialer.R;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.ExpirableCache;

import com.android.dialer.calllog.CallLogAdapterHelper.NumberWithCountryIso;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Adapter class to fill in data for the Call Log.
 */
public class CallLogAdapter extends GroupingListAdapter
        implements CallLogAdapterHelper.Callback, CallLogGroupBuilder.GroupCreator {

    private static final int VOICEMAIL_TRANSCRIPTION_MAX_LINES = 10;

    /** The enumeration of {@link android.os.AsyncTask} objects used in this class. */
    public enum Tasks {
        REMOVE_CALL_LOG_ENTRIES,
    }

    /** Interface used to inform a parent UI element that a list item has been expanded. */
    public interface CallItemExpandedListener {
        /**
         * @param view The {@link CallLogListItemView} that represents the item that was clicked
         *         on.
         */
        public void onItemExpanded(CallLogListItemView view);

        /**
         * Retrieves the call log view for the specified call Id.  If the view is not currently
         * visible, returns null.
         *
         * @param callId The call Id.
         * @return The call log view.
         */
        public CallLogListItemView getViewForCallId(long callId);
    }

    /** Interface used to initiate a refresh of the content. */
    public interface CallFetcher {
        public void fetchCalls();
    }

    /** Implements onClickListener for the report button. */
    public interface OnReportButtonClickListener {
        public void onReportButtonClick(String number);
    }

    /** Constant used to indicate no row is expanded. */
    private static final long NONE_EXPANDED = -1;

    protected final Context mContext;
    private final ContactInfoHelper mContactInfoHelper;
    private final CallFetcher mCallFetcher;
    private final Toast mReportedToast;
    private final OnReportButtonClickListener mOnReportButtonClickListener;

    private String mFilterString;

    /**
     * Tracks the call log row which was previously expanded.  Used so that the closure of a
     * previously expanded call log entry can be animated on rebind.
     */
    private long mPreviouslyExpanded = NONE_EXPANDED;

    /**
     * Tracks the currently expanded call log row.
     */
    private long mCurrentlyExpanded = NONE_EXPANDED;

    /**
     *  Hashmap, keyed by call Id, used to track the day group for a call.  As call log entries are
     *  put into the primary call groups in {@link com.android.dialer.calllog.CallLogGroupBuilder},
     *  they are also assigned a secondary "day group".  This hashmap tracks the day group assigned
     *  to all calls in the call log.  This information is used to trigger the display of a day
     *  group header above the call log entry at the start of a day group.
     *  Note: Multiple calls are grouped into a single primary "call group" in the call log, and
     *  the cursor used to bind rows includes all of these calls.  When determining if a day group
     *  change has occurred it is necessary to look at the last entry in the call log to determine
     *  its day group.  This hashmap provides a means of determining the previous day group without
     *  having to reverse the cursor to the start of the previous day call log entry.
     */
    private HashMap<Long,Integer> mDayGroups = new HashMap<Long, Integer>();

    private boolean mLoading = true;

    /** Instance of helper class for managing views. */
    private final CallLogListItemHelper mCallLogViewsHelper;

    /** Helper to set up contact photos. */
    private final ContactPhotoManager mContactPhotoManager;
    /** Helper to parse and process phone numbers. */
    private PhoneNumberDisplayHelper mPhoneNumberHelper;
    /** Helper to group call log entries. */
    private final CallLogGroupBuilder mCallLogGroupBuilder;

    private CallItemExpandedListener mCallItemExpandedListener;

    private final CallLogAdapterHelper mAdapterHelper;

    private boolean mIsCallLog = true;

    private View mBadgeContainer;
    private ImageView mBadgeImageView;
    private TextView mBadgeText;

    private int mCallLogBackgroundColor;
    private int mExpandedBackgroundColor;
    private float mExpandedTranslationZ;

    /** Listener for the primary or secondary actions in the list.
     *  Primary opens the call details.
     *  Secondary calls or plays.
     **/
    private final View.OnClickListener mActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivityForAction(view);
        }
    };

    /**
     * The onClickListener used to expand or collapse the action buttons section for a call log
     * entry.
     */
    private final View.OnClickListener mExpandCollapseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            final CallLogListItemView callLogItem = (CallLogListItemView) v.getParent().getParent();
            handleRowExpanded(callLogItem, true /* animate */, false /* forceExpand */);
        }
    };

    private AccessibilityDelegate mAccessibilityDelegate = new AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                handleRowExpanded((CallLogListItemView) host, false /* animate */,
                        true /* forceExpand */);
            }
            return super.onRequestSendAccessibilityEvent(host, child, event);
        }
    };

    private void startActivityForAction(View view) {
        final IntentProvider intentProvider = (IntentProvider) view.getTag();
        if (intentProvider != null) {
            final Intent intent = intentProvider.getIntent(mContext);
            // See IntentProvider.getCallDetailIntentProvider() for why this may be null.
            if (intent != null) {
                DialerUtils.startActivityWithErrorToast(mContext, intent);
            }
        }
    }

    public CallLogAdapter(Context context, CallFetcher callFetcher,
            ContactInfoHelper contactInfoHelper, CallItemExpandedListener callItemExpandedListener,
            OnReportButtonClickListener onReportButtonClickListener, boolean isCallLog) {
        super(context);

        mContext = context;
        mCallFetcher = callFetcher;
        mContactInfoHelper = contactInfoHelper;
        mIsCallLog = isCallLog;
        mCallItemExpandedListener = callItemExpandedListener;

        mOnReportButtonClickListener = onReportButtonClickListener;
        mReportedToast = Toast.makeText(mContext, R.string.toast_caller_id_reported,
                Toast.LENGTH_SHORT);

        Resources resources = mContext.getResources();
        CallTypeHelper callTypeHelper = new CallTypeHelper(resources);
        mCallLogBackgroundColor = resources.getColor(R.color.background_dialer_list_items);
        mExpandedBackgroundColor = resources.getColor(R.color.call_log_expanded_background_color);
        mExpandedTranslationZ = resources.getDimension(R.dimen.call_log_expanded_translation_z);

        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mPhoneNumberHelper = new PhoneNumberDisplayHelper(resources);
        mAdapterHelper = new CallLogAdapterHelper(context, this,
                contactInfoHelper, mPhoneNumberHelper);
        PhoneCallDetailsHelper phoneCallDetailsHelper = new PhoneCallDetailsHelper(
                resources, callTypeHelper, new PhoneNumberUtilsWrapper());
        mCallLogViewsHelper =
                new CallLogListItemHelper(
                        phoneCallDetailsHelper, mPhoneNumberHelper, resources);
        mCallLogGroupBuilder = new CallLogGroupBuilder(this);
    }

    /**
     * Requery on background thread when {@link Cursor} changes.
     */
    @Override
    protected void onContentChanged() {
        mCallFetcher.fetchCalls();
    }

    public void setLoading(boolean loading) {
        mLoading = loading;
    }

    @Override
    public boolean isEmpty() {
        if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    @Override
    protected void addGroups(Cursor cursor) {
        mCallLogGroupBuilder.addGroups(cursor);
    }

    @Override
    protected View newStandAloneView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newGroupView(Context context, ViewGroup parent) {
        return newChildView(context, parent);
    }

    @Override
    protected View newChildView(Context context, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        CallLogListItemView view =
                (CallLogListItemView) inflater.inflate(R.layout.call_log_list_item, parent, false);

        // Get the views to bind to and cache them.
        CallLogListItemViews views = CallLogListItemViews.fromView(view);
        view.setTag(views);

        // Set text height to false on the TextViews so they don't have extra padding.
        views.phoneCallDetailsViews.nameView.setElegantTextHeight(false);
        views.phoneCallDetailsViews.callLocationAndDate.setElegantTextHeight(false);

        return view;
    }

    @Override
    protected void bindStandAloneView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor) {
        bindView(view, cursor, 1);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
            boolean expanded) {
        bindView(view, cursor, groupSize);
    }

    private void findAndCacheViews(View view) {
    }

    /**
     * Binds the views in the entry to the data in the call log.
     *
     * @param view the view corresponding to this entry
     * @param c the cursor pointing to the entry in the call log
     * @param count the number of entries in the current item, greater than 1 if it is a group
     */
    private void bindView(View view, Cursor c, int count) {
        view.setAccessibilityDelegate(mAccessibilityDelegate);
        final CallLogListItemView callLogItemView = (CallLogListItemView) view;
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        // Default case: an item in the call log.
        views.primaryActionView.setVisibility(View.VISIBLE);

        final String number = c.getString(CallLogQuery.NUMBER);
        final int numberPresentation = c.getInt(CallLogQuery.NUMBER_PRESENTATION);
        final long date = c.getLong(CallLogQuery.DATE);
        final long duration = c.getLong(CallLogQuery.DURATION);
        final int callType = c.getInt(CallLogQuery.CALL_TYPE);
        final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                c.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME),
                c.getString(CallLogQuery.ACCOUNT_ID));
        final Drawable accountIcon = PhoneAccountUtils.getAccountIcon(mContext,
                accountHandle);
        final String countryIso = c.getString(CallLogQuery.COUNTRY_ISO);

        final long rowId = c.getLong(CallLogQuery.ID);
        views.rowId = rowId;

        String accId = c.getString(CallLogQuery.ACCOUNT_ID);
        long subId = SubscriptionManager.DEFAULT_SUB_ID;
        if (accId!= null && !accId.equals("E") && !accId.toLowerCase().contains("sip")) {
             subId = Long.parseLong(accId);
        }

        // For entries in the call log, check if the day group has changed and display a header
        // if necessary.
        if (mIsCallLog) {
            int currentGroup = getDayGroupForCall(rowId);
            int previousGroup = getPreviousDayGroup(c);
            if (currentGroup != previousGroup) {
                views.dayGroupHeader.setVisibility(View.VISIBLE);
                views.dayGroupHeader.setText(getGroupDescription(currentGroup));
            } else {
                views.dayGroupHeader.setVisibility(View.GONE);
            }
        } else {
            views.dayGroupHeader.setVisibility(View.GONE);
        }

        // Store some values used when the actions ViewStub is inflated on expansion of the actions
        // section.
        views.number = number;
        views.numberPresentation = numberPresentation;
        views.callType = callType;
        // NOTE: This is currently not being used, but can be used in future versions.
        views.accountHandle = accountHandle;
        views.voicemailUri = c.getString(CallLogQuery.VOICEMAIL_URI);
        // Stash away the Ids of the calls so that we can support deleting a row in the call log.
        views.callIds = getCallIds(c, count);

        final ContactInfo cachedContactInfo = getContactInfoFromCallLog(c);

        final boolean isVoicemailNumber =
                PhoneNumberUtilsWrapper.INSTANCE.isVoicemailNumber(subId, number);

        // Where binding and not in the call log, use default behaviour of invoking a call when
        // tapping the primary view.
        if (!mIsCallLog) {
            views.primaryActionView.setOnClickListener(this.mActionListener);

            // Set return call intent, otherwise null.
            if (PhoneNumberUtilsWrapper.canPlaceCallsTo(number, numberPresentation)) {
                // Sets the primary action to call the number.
                views.primaryActionView.setTag(IntentProvider.getReturnCallIntentProvider(number));
            } else {
                // Number is not callable, so hide button.
                views.primaryActionView.setTag(null);
            }
        } else {
            // In the call log, expand/collapse an actions section for the call log entry when
            // the primary view is tapped.
            views.primaryActionView.setOnClickListener(this.mExpandCollapseListener);

            // Note: Binding of the action buttons is done as required in configureActionViews
            // when the user expands the actions ViewStub.
        }

        // Lookup contacts with this number
        final ContactInfo info = mAdapterHelper.lookupContact(
                number, numberPresentation, countryIso, cachedContactInfo);
        final Uri lookupUri = info.lookupUri;
        final String name = info.name;
        final int ntype = info.type;
        final String label = info.label;
        final long photoId = info.photoId;
        final Uri photoUri = info.photoUri;
        CharSequence formattedNumber = info.formattedNumber;
        final int[] callTypes = getCallTypes(c, count);
        final String geocode = c.getString(CallLogQuery.GEOCODED_LOCATION);
        final int sourceType = info.sourceType;
        final int features = getCallFeatures(c, count);
        final String transcription = c.getString(CallLogQuery.TRANSCRIPTION);
        Long dataUsage = null;
        if (!c.isNull(CallLogQuery.DATA_USAGE)) {
            dataUsage = c.getLong(CallLogQuery.DATA_USAGE);
        }

        final PhoneCallDetails details;
        final String accountName = info.accountName;
        final String accountType = info.accountType;
        Account contactAccount;

        views.reported = info.isBadData;

        // The entry can only be reported as invalid if it has a valid ID and the source of the
        // entry supports marking entries as invalid.
        views.canBeReportedAsInvalid = mContactInfoHelper.canReportAsInvalid(info.sourceType,
                info.objectId);

        // Restore expansion state of the row on rebind.  Inflate the actions ViewStub if required,
        // and set its visibility state accordingly.
        expandOrCollapseActions(callLogItemView, isExpanded(rowId));

        if (TextUtils.isEmpty(name)) {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration, null, accountIcon, features, dataUsage, transcription, subId);
        } else {
            details = new PhoneCallDetails(number, numberPresentation,
                    formattedNumber, countryIso, geocode, callTypes, date,
                    duration, name, ntype, label, lookupUri, photoUri, sourceType,
                    null, accountIcon, features, dataUsage, transcription,
                    Calls.DURATION_TYPE_ACTIVE, subId);
        }

        mCallLogViewsHelper.setPhoneCallDetails(mContext, views, details);

        int contactType = ContactPhotoManager.TYPE_DEFAULT;

        if (isVoicemailNumber) {
            contactType = ContactPhotoManager.TYPE_VOICEMAIL;
        } else if (mContactInfoHelper.isBusiness(info.sourceType)) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        String lookupKey = lookupUri == null ? null
                : ContactInfoHelper.getLookupKeyFromUri(lookupUri);

        String nameForDefaultImage = null;
        if (TextUtils.isEmpty(name)) {
            nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(details.accountId,
                    details.number, details.numberPresentation,
                    details.formattedNumber).toString();
        } else {
            nameForDefaultImage = name;
        }

        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
            contactAccount = new Account(accountName, accountType);
        } else {
            contactAccount = null;
        }
        if (photoId == 0 && photoUri != null) {
            setPhoto(views, photoUri, lookupUri, nameForDefaultImage, lookupKey, contactType, contactAccount);
        } else {
            setPhoto(views, photoId, lookupUri, nameForDefaultImage, lookupKey, contactType, contactAccount);
        }

        // Listen for the first draw
        mAdapterHelper.registerOnPreDrawListener(view);

        bindBadge(view, info, details, callType);
    }

    /**
     * Retrieves the day group of the previous call in the call log.  Used to determine if the day
     * group has changed and to trigger display of the day group text.
     *
     * @param cursor The call log cursor.
     * @return The previous day group, or DAY_GROUP_NONE if this is the first call.
     */
    private int getPreviousDayGroup(Cursor cursor) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        int dayGroup = CallLogGroupBuilder.DAY_GROUP_NONE;
        if (cursor.moveToPrevious()) {
            long previousRowId = cursor.getLong(CallLogQuery.ID);
            dayGroup = getDayGroupForCall(previousRowId);
        }
        cursor.moveToPosition(startingPosition);
        return dayGroup;
    }

    /**
     * Given a call Id, look up the day group that the call belongs to.  The day group data is
     * populated in {@link com.android.dialer.calllog.CallLogGroupBuilder}.
     *
     * @param callId The call to retrieve the day group for.
     * @return The day group for the call.
     */
    private int getDayGroupForCall(long callId) {
        if (mDayGroups.containsKey(callId)) {
            return mDayGroups.get(callId);
        }
        return CallLogGroupBuilder.DAY_GROUP_NONE;
    }
    /**
     * Determines if a call log row with the given Id is expanded.
     * @param rowId The row Id of the call.
     * @return True if the row should be expanded.
     */
    private boolean isExpanded(long rowId) {
        return mCurrentlyExpanded == rowId;
    }

    /**
     * Toggles the expansion state tracked for the call log row identified by rowId and returns
     * the new expansion state.  Assumes that only a single call log row will be expanded at any
     * one point and tracks the current and previous expanded item.
     *
     * @param rowId The row Id associated with the call log row to expand/collapse.
     * @return True where the row is now expanded, false otherwise.
     */
    private boolean toggleExpansion(long rowId) {
        if (rowId == mCurrentlyExpanded) {
            // Collapsing currently expanded row.
            mPreviouslyExpanded = NONE_EXPANDED;
            mCurrentlyExpanded = NONE_EXPANDED;

            return false;
        } else {
            // Expanding a row (collapsing current expanded one).

            mPreviouslyExpanded = mCurrentlyExpanded;
            mCurrentlyExpanded = rowId;
            return true;
        }
    }

    /**
     * Expands or collapses the view containing the CALLBACK, VOICEMAIL and DETAILS action buttons.
     *
     * @param callLogItem The call log entry parent view.
     * @param isExpanded The new expansion state of the view.
     */
    private void expandOrCollapseActions(CallLogListItemView callLogItem, boolean isExpanded) {
        final CallLogListItemViews views = (CallLogListItemViews)callLogItem.getTag();

        expandVoicemailTranscriptionView(views, isExpanded);
        if (isExpanded) {
            // Inflate the view stub if necessary, and wire up the event handlers.
            inflateActionViewStub(callLogItem);

            views.actionsView.setVisibility(View.VISIBLE);
            views.actionsView.setAlpha(1.0f);
            views.callLogEntryView.setBackgroundColor(mExpandedBackgroundColor);
            views.callLogEntryView.setTranslationZ(mExpandedTranslationZ);
            callLogItem.setTranslationZ(mExpandedTranslationZ); // WAR
        } else {
            // When recycling a view, it is possible the actionsView ViewStub was previously
            // inflated so we should hide it in this case.
            if (views.actionsView != null) {
                views.actionsView.setVisibility(View.GONE);
            }

            views.callLogEntryView.setBackgroundColor(mCallLogBackgroundColor);
            views.callLogEntryView.setTranslationZ(0);
            callLogItem.setTranslationZ(0); // WAR
        }
    }

    public static void expandVoicemailTranscriptionView(CallLogListItemViews views,
            boolean isExpanded) {
        if (views.callType != Calls.VOICEMAIL_TYPE) {
            return;
        }

        final TextView view = views.phoneCallDetailsViews.voicemailTranscriptionView;
        if (TextUtils.isEmpty(view.getText())) {
            return;
        }
        view.setMaxLines(isExpanded ? VOICEMAIL_TRANSCRIPTION_MAX_LINES : 1);
        view.setSingleLine(!isExpanded);
    }

    /**
     * Configures the action buttons in the expandable actions ViewStub.  The ViewStub is not
     * inflated during initial binding, so click handlers, tags and accessibility text must be set
     * here, if necessary.
     *
     * @param callLogItem The call log list item view.
     */
    private void inflateActionViewStub(final View callLogItem) {
        final CallLogListItemViews views = (CallLogListItemViews)callLogItem.getTag();

        ViewStub stub = (ViewStub)callLogItem.findViewById(R.id.call_log_entry_actions_stub);
        if (stub != null) {
            views.actionsView = (ViewGroup) stub.inflate();
        }

        if (views.callBackButtonView == null) {
            views.callBackButtonView = (TextView)views.actionsView.findViewById(
                    R.id.call_back_action);
        }

        if (views.videoCallButtonView == null) {
            views.videoCallButtonView = (TextView)views.actionsView.findViewById(
                    R.id.video_call_action);
        }

        if (views.voicemailButtonView == null) {
            views.voicemailButtonView = (TextView)views.actionsView.findViewById(
                    R.id.voicemail_action);
        }

        if (views.detailsButtonView == null) {
            views.detailsButtonView = (TextView)views.actionsView.findViewById(R.id.details_action);
        }

        if (views.reportButtonView == null) {
            views.reportButtonView = (TextView)views.actionsView.findViewById(R.id.report_action);
            views.reportButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mOnReportButtonClickListener != null) {
                        mOnReportButtonClickListener.onReportButtonClick(views.number);
                    }
                }
            });
        }

        bindActionButtons(views);
    }

    /***
     * Binds click handlers and intents to the voicemail, details and callback action buttons.
     *
     * @param views  The call log item views.
     */
    private void bindActionButtons(CallLogListItemViews views) {
        boolean canPlaceCallToNumber =
                PhoneNumberUtilsWrapper.canPlaceCallsTo(views.number, views.numberPresentation);
        // Set return call intent, otherwise null.
        if (canPlaceCallToNumber) {
            // Sets the primary action to call the number.
            views.callBackButtonView.setTag(
                    IntentProvider.getReturnCallIntentProvider(views.number));
            views.callBackButtonView.setVisibility(View.VISIBLE);
            views.callBackButtonView.setOnClickListener(mActionListener);
        } else {
            // Number is not callable, so hide button.
            views.callBackButtonView.setTag(null);
            views.callBackButtonView.setVisibility(View.GONE);
        }

        // If one of the calls had video capabilities, show the video call button.
        if (CallUtil.isVideoEnabled(mContext) && canPlaceCallToNumber &&
                views.phoneCallDetailsViews.callTypeIcons.isVideoShown()) {
            views.videoCallButtonView.setTag(
                    IntentProvider.getReturnVideoCallIntentProvider(views.number));
            views.videoCallButtonView.setVisibility(View.VISIBLE);
            views.videoCallButtonView.setOnClickListener(mActionListener);
        } else {
            views.videoCallButtonView.setTag(null);
            views.videoCallButtonView.setVisibility(View.GONE);
        }

        // if csvt is enabled, shoue video call options.
        if (CallUtil.isCSVTEnabled() && canPlaceCallToNumber) {
            views.videoCallButtonView.setTag(
                    IntentProvider.getReturnCSVTCallIntentProvider(views.number));
            views.videoCallButtonView.setVisibility(View.VISIBLE);
            views.videoCallButtonView.setOnClickListener(mActionListener);
        } else {
            views.videoCallButtonView.setTag(null);
            views.videoCallButtonView.setVisibility(View.GONE);
        }

        // For voicemail calls, show the "VOICEMAIL" action button; hide otherwise.
        if (views.callType == Calls.VOICEMAIL_TYPE) {
            views.voicemailButtonView.setOnClickListener(mActionListener);
            views.voicemailButtonView.setTag(
                    IntentProvider.getPlayVoicemailIntentProvider(
                            views.rowId, views.voicemailUri));
            views.voicemailButtonView.setVisibility(View.VISIBLE);

            views.detailsButtonView.setVisibility(View.GONE);
        } else {
            views.voicemailButtonView.setTag(null);
            views.voicemailButtonView.setVisibility(View.GONE);

            views.detailsButtonView.setOnClickListener(mActionListener);
            views.detailsButtonView.setTag(
                    IntentProvider.getCallDetailIntentProvider(
                            views.rowId, views.callIds, null)
            );

            if (views.canBeReportedAsInvalid && !views.reported) {
                views.reportButtonView.setVisibility(View.VISIBLE);
            } else {
                views.reportButtonView.setVisibility(View.GONE);
            }
        }

        mCallLogViewsHelper.setActionContentDescriptions(views);
    }

    protected void bindBadge(
            View view, ContactInfo info, final PhoneCallDetails details, int callType) {
        // Do not show badge in call log.
        if (!mIsCallLog) {
            final ViewStub stub = (ViewStub) view.findViewById(R.id.link_stub);
            if (UriUtils.isEncodedContactUri(info.lookupUri)) {
                if (stub != null) {
                    final View inflated = stub.inflate();
                    inflated.setVisibility(View.VISIBLE);
                    mBadgeContainer = inflated.findViewById(R.id.badge_link_container);
                    mBadgeImageView = (ImageView) inflated.findViewById(R.id.badge_image);
                    mBadgeText = (TextView) inflated.findViewById(R.id.badge_text);
                }

                mBadgeContainer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Intent intent =
                                DialtactsActivity.getAddNumberToContactIntent(details.number);
                        mContext.startActivity(intent);
                    }
                });
                mBadgeImageView.setImageResource(R.drawable.ic_person_add_24dp);
                mBadgeText.setText(R.string.recentCalls_addToContact);
            } else {
                // Hide badge if it was previously shown.
                if (stub == null) {
                    final View container = view.findViewById(R.id.badge_container);
                    if (container != null) {
                        container.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    @Override
    public void dataSetChanged() {
        notifyDataSetChanged();
    }

    /** Stores the updated contact info in the call log if it is different from the current one. */
    @Override
    public void updateContactInfo(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        final ContentValues values = new ContentValues();
        boolean needsUpdate = false;

        if (callLogInfo != null) {
            if (!TextUtils.equals(updatedInfo.name, callLogInfo.name)) {
                values.put(Calls.CACHED_NAME, updatedInfo.name);
                needsUpdate = true;
            }

            if (updatedInfo.type != callLogInfo.type) {
                values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
                needsUpdate = true;
            }

            if (!TextUtils.equals(updatedInfo.label, callLogInfo.label)) {
                values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
                needsUpdate = true;
            }
            if (!UriUtils.areEqual(updatedInfo.lookupUri, callLogInfo.lookupUri)) {
                values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
                needsUpdate = true;
            }
            // Only replace the normalized number if the new updated normalized number isn't empty.
            if (!TextUtils.isEmpty(updatedInfo.normalizedNumber) &&
                    !TextUtils.equals(updatedInfo.normalizedNumber, callLogInfo.normalizedNumber)) {
                values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.number, callLogInfo.number)) {
                values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
                needsUpdate = true;
            }
            if (updatedInfo.photoId != callLogInfo.photoId) {
                values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
                needsUpdate = true;
            }
            if (!TextUtils.equals(updatedInfo.formattedNumber, callLogInfo.formattedNumber)) {
                values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
                needsUpdate = true;
            }
        } else {
            // No previous values, store all of them.
            values.put(Calls.CACHED_NAME, updatedInfo.name);
            values.put(Calls.CACHED_NUMBER_TYPE, updatedInfo.type);
            values.put(Calls.CACHED_NUMBER_LABEL, updatedInfo.label);
            values.put(Calls.CACHED_LOOKUP_URI, UriUtils.uriToString(updatedInfo.lookupUri));
            values.put(Calls.CACHED_MATCHED_NUMBER, updatedInfo.number);
            values.put(Calls.CACHED_NORMALIZED_NUMBER, updatedInfo.normalizedNumber);
            values.put(Calls.CACHED_PHOTO_ID, updatedInfo.photoId);
            values.put(Calls.CACHED_FORMATTED_NUMBER, updatedInfo.formattedNumber);
            needsUpdate = true;
        }

        if (!needsUpdate) return;

        if (countryIso == null) {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " IS NULL",
                    new String[]{ number });
        } else {
            mContext.getContentResolver().update(Calls.CONTENT_URI_WITH_VOICEMAIL, values,
                    Calls.NUMBER + " = ? AND " + Calls.COUNTRY_ISO + " = ?",
                    new String[]{ number, countryIso });
        }
    }

    /** Returns the contact information as stored in the call log. */
    private ContactInfo getContactInfoFromCallLog(Cursor c) {
        ContactInfo info = new ContactInfo();
        info.lookupUri = UriUtils.parseUriOrNull(c.getString(CallLogQuery.CACHED_LOOKUP_URI));
        info.name = c.getString(CallLogQuery.CACHED_NAME);
        info.type = c.getInt(CallLogQuery.CACHED_NUMBER_TYPE);
        info.label = c.getString(CallLogQuery.CACHED_NUMBER_LABEL);
        String matchedNumber = c.getString(CallLogQuery.CACHED_MATCHED_NUMBER);
        info.number = matchedNumber == null ? c.getString(CallLogQuery.NUMBER) : matchedNumber;
        info.normalizedNumber = c.getString(CallLogQuery.CACHED_NORMALIZED_NUMBER);
        info.photoId = c.getLong(CallLogQuery.CACHED_PHOTO_ID);
        info.photoUri = null;  // We do not cache the photo URI.
        info.formattedNumber = c.getString(CallLogQuery.CACHED_FORMATTED_NUMBER);
        return info;
    }

    /**
     * Returns the call types for the given number of items in the cursor.
     * <p>
     * It uses the next {@code count} rows in the cursor to extract the types.
     * <p>
     * It position in the cursor is unchanged by this function.
     */
    private int[] getCallTypes(Cursor cursor, int count) {
        int position = cursor.getPosition();
        int[] callTypes = new int[count];
        for (int index = 0; index < count; ++index) {
            callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return callTypes;
    }

    /**
     * Determine the features which were enabled for any of the calls that make up a call log
     * entry.
     *
     * @param cursor The cursor.
     * @param count The number of calls for the current call log entry.
     * @return The features.
     */
    private int getCallFeatures(Cursor cursor, int count) {
        int features = 0;
        int position = cursor.getPosition();
        for (int index = 0; index < count; ++index) {
            features |= cursor.getInt(CallLogQuery.FEATURES);
            cursor.moveToNext();
        }
        cursor.moveToPosition(position);
        return features;
    }

    private void setPhoto(CallLogListItemViews views, long photoId, Uri contactUri,
            String displayName, String identifier, int contactType, Account account) {
        views.quickContactView.assignContactUri(contactUri);
        views.quickContactView.setOverlay(null);
        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier,
                contactType, true /* isCircular */);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, account,
                false /* darkTheme */, true /* isCircular */, request);
    }

    private void setPhoto(CallLogListItemViews views, Uri photoUri, Uri contactUri,
            String displayName, String identifier, int contactType, Account account) {
        views.quickContactView.assignContactUri(contactUri);
        views.quickContactView.setOverlay(null);
        DefaultImageRequest request = new DefaultImageRequest(displayName, identifier,
                contactType, true /* isCircular */);
        mContactPhotoManager.loadDirectoryPhoto(views.quickContactView, photoUri, account,
                false /* darkTheme */, true /* isCircular */, request);
    }

    /**
     * Bind a call log entry view for testing purposes.  Also inflates the action view stub so
     * unit tests can access the buttons contained within.
     *
     * @param view The current call log row.
     * @param context The current context.
     * @param cursor The cursor to bind from.
     */
    @VisibleForTesting
    void bindViewForTest(View view, Context context, Cursor cursor) {
        bindStandAloneView(view, context, cursor);
        inflateActionViewStub(view);
    }

    /**
     * Sets whether processing of requests for contact details should be enabled.
     * <p>
     * This method should be called in tests to disable such processing of requests when not
     * needed.
     */
    @VisibleForTesting
    void disableRequestProcessingForTest() {
        mAdapterHelper.disableRequestProcessingForTest();
    }

    @VisibleForTesting
    void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
        mAdapterHelper.injectContactInfoForTest(number, countryIso, contactInfo);
    }

    @VisibleForTesting
    void enqueueRequest(String number, String countryIso, ContactInfo callLogInfo,
            boolean immediate) {
        mAdapterHelper.enqueueRequest(number, countryIso, callLogInfo, immediate);
    }

    @Override
    public void addGroup(int cursorPosition, int size, boolean expanded) {
        super.addGroup(cursorPosition, size, expanded);
    }

    /**
     * Stores the day group associated with a call in the call log.
     *
     * @param rowId The row Id of the current call.
     * @param dayGroup The day group the call belongs in.
     */
    @Override
    public void setDayGroup(long rowId, int dayGroup) {
        if (!mDayGroups.containsKey(rowId)) {
            mDayGroups.put(rowId, dayGroup);
        }
    }

    /**
     * Clears the day group associations on re-bind of the call log.
     */
    @Override
    public void clearDayGroups() {
        mDayGroups.clear();
    }

    public void stopRequestProcessing() {
        mAdapterHelper.stopRequestProcessing();
    }

    public void invalidateCache() {
        mAdapterHelper.invalidateCache();
    }

    public String getBetterNumberFromContacts(String number, String countryIso) {
        return mAdapterHelper.getBetterNumberFromContacts(number, countryIso);
    }

    /**
     * Retrieves the call Ids represented by the current call log row.
     *
     * @param cursor Call log cursor to retrieve call Ids from.
     * @param groupSize Number of calls associated with the current call log row.
     * @return Array of call Ids.
     */
    private long[] getCallIds(final Cursor cursor, final int groupSize) {
        // We want to restore the position in the cursor at the end.
        int startingPosition = cursor.getPosition();
        long[] ids = new long[groupSize];
        // Copy the ids of the rows in the group.
        for (int index = 0; index < groupSize; ++index) {
            ids[index] = cursor.getLong(CallLogQuery.ID);
            cursor.moveToNext();
        }
        cursor.moveToPosition(startingPosition);
        return ids;
    }

    /**
     * Determines the description for a day group.
     *
     * @param group The day group to retrieve the description for.
     * @return The day group description.
     */
    private CharSequence getGroupDescription(int group) {
       if (group == CallLogGroupBuilder.DAY_GROUP_TODAY) {
           return mContext.getResources().getString(R.string.call_log_header_today);
       } else if (group == CallLogGroupBuilder.DAY_GROUP_YESTERDAY) {
           return mContext.getResources().getString(R.string.call_log_header_yesterday);
       } else {
           return mContext.getResources().getString(R.string.call_log_header_other);
       }
    }

    public void onBadDataReported(String number) {
        mReportedToast.show();
    }

    /**
     * Manages the state changes for the UI interaction where a call log row is expanded.
     *
     * @param view The view that was tapped
     * @param animate Whether or not to animate the expansion/collapse
     * @param forceExpand Whether or not to force the call log row into an expanded state regardless
     *        of its previous state
     */
    private void handleRowExpanded(CallLogListItemView view, boolean animate, boolean forceExpand) {
        final CallLogListItemViews views = (CallLogListItemViews) view.getTag();

        if (forceExpand && isExpanded(views.rowId)) {
            return;
        }

        // Hide or show the actions view.
        boolean expanded = toggleExpansion(views.rowId);

        // Trigger loading of the viewstub and visual expand or collapse.
        expandOrCollapseActions(view, expanded);

        // Animate the expansion or collapse.
        if (mCallItemExpandedListener != null) {
            if (animate) {
                mCallItemExpandedListener.onItemExpanded(view);
            }

            // Animate the collapse of the previous item if it is still visible on screen.
            if (mPreviouslyExpanded != NONE_EXPANDED) {
                CallLogListItemView previousItem = mCallItemExpandedListener.getViewForCallId(
                        mPreviouslyExpanded);

                if (previousItem != null) {
                    expandOrCollapseActions(previousItem, false);
                    if (animate) {
                        mCallItemExpandedListener.onItemExpanded(previousItem);
                    }
                }
                mPreviouslyExpanded = NONE_EXPANDED;
            }
        }
    }

    public void setQueryString(String filter) {
        mFilterString = filter;
    }
}
