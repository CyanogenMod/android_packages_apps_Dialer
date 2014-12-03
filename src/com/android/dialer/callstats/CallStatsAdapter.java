/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2013 Android Open Kang Project
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

package com.android.dialer.callstats;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.common.GeoUtil;
import com.android.dialer.R;
import com.android.dialer.calllog.CallLogAdapterHelper;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.calllog.ContactInfoHelper;
import com.android.dialer.calllog.PhoneNumberDisplayHelper;
import com.android.dialer.calllog.PhoneNumberUtilsWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter class to hold and handle call stat entries
 */
class CallStatsAdapter extends ArrayAdapter<CallStatsDetails>
        implements CallLogAdapterHelper.Callback {
    /** Interface used to initiate a refresh of the content. */
    public interface CallDataLoader {
        public boolean isDataLoaded();
    }

    private final View.OnClickListener mPrimaryActionListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            IntentProvider intentProvider = (IntentProvider) view.getTag();
            if (intentProvider != null) {
                mContext.startActivity(intentProvider.getIntent(mContext));
            }
        }
    };

    private final Context mContext;
    private final CallDataLoader mDataLoader;
    private final CallLogAdapterHelper mAdapterHelper;
    private final CallStatsDetailHelper mCallStatsDetailHelper;

    private ArrayList<CallStatsDetails> mAllItems;
    private CallStatsDetails mTotalItem;
    private Map<ContactInfo, CallStatsDetails> mInfoLookup;

    private int mType = CallStatsQueryHandler.CALL_TYPE_ALL;
    private long mFilterFrom;
    private long mFilterTo;
    private boolean mSortByDuration;

    private final ContactPhotoManager mContactPhotoManager;
    private PhoneNumberDisplayHelper mPhoneNumberHelper;

    private final Comparator<CallStatsDetails> mDurationComparator = new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Long duration1 = o1.getRequestedDuration(mType);
            Long duration2 = o2.getRequestedDuration(mType);
            // sort descending
            return duration2.compareTo(duration1);
        }
    };
    private final Comparator<CallStatsDetails> mCountComparator = new Comparator<CallStatsDetails>() {
        @Override
        public int compare(CallStatsDetails o1, CallStatsDetails o2) {
            Integer count1 = o1.getRequestedCount(mType);
            Integer count2 = o2.getRequestedCount(mType);
            // sort descending
            return count2.compareTo(count1);
        }
    };

    CallStatsAdapter(Context context, CallDataLoader dataLoader) {
        super(context, R.layout.call_stats_list_item, R.id.number);

        mContext = context;
        mDataLoader = dataLoader;

        setNotifyOnChange(false);

        mAllItems = new ArrayList<CallStatsDetails>();
        mTotalItem = new CallStatsDetails(null, 0, 0, null, null, null, 0);
        mInfoLookup = new ConcurrentHashMap<ContactInfo, CallStatsDetails>();

        Resources resources = mContext.getResources();
        mPhoneNumberHelper = new PhoneNumberDisplayHelper(resources);

        final String currentCountryIso = GeoUtil.getCurrentCountryIso(mContext);
        final ContactInfoHelper contactInfoHelper =
                new ContactInfoHelper(mContext, currentCountryIso);

        mAdapterHelper = new CallLogAdapterHelper(mContext, this,
                contactInfoHelper, mPhoneNumberHelper);
        mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
        mCallStatsDetailHelper = new CallStatsDetailHelper(resources,
                new PhoneNumberUtilsWrapper());
    }

    public void updateData(Map<ContactInfo, CallStatsDetails> calls, long from, long to) {
        mInfoLookup.clear();
        mInfoLookup.putAll(calls);
        mFilterFrom = from;
        mFilterTo = to;

        mAllItems.clear();
        mTotalItem.reset();

        for (Map.Entry<ContactInfo, CallStatsDetails> entry : calls.entrySet()) {
            final CallStatsDetails call = entry.getValue();
            mAllItems.add(call);
            mTotalItem.mergeWith(call);
            mAdapterHelper.lookupContact(call.number, call.numberPresentation,
                    call.countryIso, entry.getKey());
        }
    }

    public void updateDisplayedData(int type, boolean sortByDuration) {
        mType = type;
        mSortByDuration = sortByDuration;
        Collections.sort(mAllItems, sortByDuration ? mDurationComparator : mCountComparator);

        clear();

        for (CallStatsDetails call : mAllItems) {
            if (sortByDuration && call.getRequestedDuration(type) > 0) {
                add(call);
            } else if (!sortByDuration && call.getRequestedCount(type) > 0) {
                add(call);
            }
        }

        notifyDataSetChanged();
    }

    public void stopRequestProcessing() {
        mAdapterHelper.stopRequestProcessing();
    }

    public String getBetterNumberFromContacts(String number, String countryIso) {
        return mAdapterHelper.getBetterNumberFromContacts(number, countryIso);
    }

    public void invalidateCache() {
        mAdapterHelper.invalidateCache();
    }

    public String getTotalCallCountString() {
        return CallStatsDetailHelper.getCallCountString(
                mContext.getResources(), mTotalItem.getRequestedCount(mType));
    }

    public String getFullDurationString(boolean withSeconds) {
        final long duration = mTotalItem.getRequestedDuration(mType);
        return CallStatsDetailHelper.getDurationString(
                mContext.getResources(), duration, withSeconds);
    }

    @Override
    public boolean isEmpty() {
        if (!mDataLoader.isDataLoaded()) {
            return false;
        }
        return super.isEmpty();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.call_stats_list_item, parent, false);
        }

        findAndCacheViews(v);
        bindView(position, v);

        return v;
    }

    private void bindView(int position, View v) {
        final CallStatsListItemViews views = (CallStatsListItemViews) v.getTag();
        final CallStatsDetails details = getItem(position);
        final CallStatsDetails first = getItem(0);

        views.primaryActionView.setTag(IntentProvider.getCallStatsDetailIntentProvider(
                details, mFilterFrom, mFilterTo, mSortByDuration));
        mCallStatsDetailHelper.setCallStatsDetails(views.callStatsDetailViews,
                details, first, mTotalItem, mType, mSortByDuration);

        String nameForDefaultImage = null;
        if (TextUtils.isEmpty(details.name)) {
            nameForDefaultImage = mPhoneNumberHelper.getDisplayNumber(details.accountId,
                    details.number, details.numberPresentation, details.formattedNumber).toString();
        } else {
            nameForDefaultImage = details.name;
        }

        setPhoto(views, details.photoId, details.contactUri, nameForDefaultImage);

        // Listen for the first draw
        mAdapterHelper.registerOnPreDrawListener(v);
    }

    private void findAndCacheViews(View view) {
        CallStatsListItemViews views = CallStatsListItemViews.fromView(view);
        views.primaryActionView.setOnClickListener(mPrimaryActionListener);
        view.setTag(views);
    }

    private void setPhoto(CallStatsListItemViews views, long photoId,
            Uri contactUri, String displayName) {
        views.quickContactView.assignContactUri(contactUri);
        views.quickContactView.setOverlay(null);

        String lookupKey = contactUri == null ? null
                : ContactInfoHelper.getLookupKeyFromUri(contactUri);
        DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey,
                ContactPhotoManager.TYPE_DEFAULT, true /* isCircular */);
        mContactPhotoManager.loadThumbnail(views.quickContactView, photoId, null,
                false /* darkTheme */, true /* isCircular */, request);
    }

    @Override
    public void dataSetChanged() {
        notifyDataSetChanged();
    }

    @Override
    public void updateContactInfo(String number, String countryIso,
            ContactInfo updatedInfo, ContactInfo callLogInfo) {
        CallStatsDetails details = mInfoLookup.get(callLogInfo);
        if (details != null) {
            details.updateFromInfo(updatedInfo);
        }
    }
}
