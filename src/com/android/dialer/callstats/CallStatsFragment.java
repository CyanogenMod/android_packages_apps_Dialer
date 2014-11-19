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

import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.android.dialer.R;
import com.android.dialer.calllog.ContactInfo;
import com.android.dialer.widget.DoubleDatePickerDialog;

import java.util.List;
import java.util.Map;

public class CallStatsFragment extends ListFragment implements
        CallStatsAdapter.CallDataLoader, CallStatsQueryHandler.Listener,
        AdapterView.OnItemSelectedListener, DoubleDatePickerDialog.OnDateSetListener {
    private static final String TAG = "CallStatsFragment";

    private Spinner mFilterSpinner;

    private int mCallTypeFilter = CallStatsQueryHandler.CALL_TYPE_ALL;
    private long mFilterFrom = -1;
    private long mFilterTo = -1;
    private boolean mSortByDuration = true;
    private boolean mDataLoaded = false;

    private CallStatsAdapter mAdapter;
    private CallStatsQueryHandler mCallStatsQueryHandler;

    private TextView mSumHeaderView;
    private TextView mDateFilterView;

    private boolean mRefreshDataRequired = true;
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    };

    public static class CallStatsNavAdapter extends ArrayAdapter<String> {
        private LayoutInflater mMainInflater;
        private LayoutInflater mDropdownInflater;

        public CallStatsNavAdapter(Context context, int textResourceId, String[] objects) {
            super(context, textResourceId, objects);
            mMainInflater = LayoutInflater.from(
                    new ContextThemeWrapper(context, R.style.DialtactsSpinnerTheme));
            mDropdownInflater = LayoutInflater.from(context);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent, mDropdownInflater);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent, mMainInflater);
        }

        public View getCustomView(int position, View convertView,
                ViewGroup parent, LayoutInflater inflater) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.call_stats_nav_item, parent, false);
            }

            TextView label = (TextView) convertView.findViewById(R.id.call_stats_nav_text);
            label.setText(getItem(position));

            return convertView;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        final ContentResolver cr = getActivity().getContentResolver();
        mCallStatsQueryHandler = new CallStatsQueryHandler(cr, this);
        cr.registerContentObserver(CallLog.CONTENT_URI, true, mObserver);
        cr.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, mObserver);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_stats_fragment, container, false);
        mSumHeaderView = (TextView) view.findViewById(R.id.sum_header);
        mDateFilterView = (TextView) view.findViewById(R.id.date_filter);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAdapter = new CallStatsAdapter(getActivity(), this);
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);
        getListView().setEmptyView(view.findViewById(R.id.empty_list_view));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.call_stats_options, menu);

        final MenuItem resetItem = menu.findItem(R.id.reset_date_filter);
        final MenuItem sortDurationItem = menu.findItem(R.id.sort_by_duration);
        final MenuItem sortCountItem = menu.findItem(R.id.sort_by_count);
        final MenuItem filterItem = menu.findItem(R.id.filter);

        resetItem.setVisible(mFilterFrom != -1);
        sortDurationItem.setVisible(!mSortByDuration);
        sortCountItem.setVisible(mSortByDuration);

        mFilterSpinner = (Spinner) filterItem.getActionView();
        CallStatsNavAdapter filterAdapter = new CallStatsNavAdapter(getActivity(),
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.call_stats_nav_items));
        mFilterSpinner.setAdapter(filterAdapter);
        mFilterSpinner.setOnItemSelectedListener(this);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            case R.id.date_filter: {
                final DoubleDatePickerDialog.Fragment fragment =
                        new DoubleDatePickerDialog.Fragment();
                fragment.setArguments(DoubleDatePickerDialog.Fragment.createArguments(
                        mFilterFrom, mFilterTo));
                fragment.show(getFragmentManager(), "filter");
                break;
            }
            case R.id.reset_date_filter: {
                mFilterFrom = -1;
                mFilterTo = -1;
                fetchCalls();
                getActivity().invalidateOptionsMenu();
                break;
            }
            case R.id.sort_by_duration:
            case R.id.sort_by_count: {
                mSortByDuration = itemId == R.id.sort_by_duration;
                mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
                getActivity().invalidateOptionsMenu();
                break;
            }
        }
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        mCallTypeFilter = pos;
        mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
        if (mDataLoaded) {
            updateHeader();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onDateSet(long from, long to) {
        mFilterFrom = from;
        mFilterTo = to;
        getActivity().invalidateOptionsMenu();
        fetchCalls();
    }

    /**
     * Called by the CallStatsQueryHandler when the list of calls has been
     * fetched or updated.
     */
    @Override
    public void onCallsFetched(Map<ContactInfo, CallStatsDetails> calls) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        mDataLoaded = true;
        mAdapter.updateData(calls, mFilterFrom, mFilterTo);
        mAdapter.updateDisplayedData(mCallTypeFilter, mSortByDuration);
        updateHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        getActivity().getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public boolean isDataLoaded() {
        return mDataLoaded;
    }

    private void fetchCalls() {
        mCallStatsQueryHandler.fetchCalls(mFilterFrom, mFilterTo);
    }

    private void updateHeader() {
        final String callCount = mAdapter.getTotalCallCountString();
        final String duration = mAdapter.getFullDurationString(false);

        if (duration != null) {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total, callCount, duration));
        } else {
            mSumHeaderView.setText(getString(R.string.call_stats_header_total_callsonly, callCount));
        }

        if (mFilterFrom == -1) {
            mDateFilterView.setVisibility(View.GONE);
        } else {
            mDateFilterView.setText(DateUtils.formatDateRange(getActivity(),
                    mFilterFrom, mFilterTo, 0));
            mDateFilterView.setVisibility(View.VISIBLE);
        }

        getView().findViewById(R.id.call_stats_header).setVisibility(View.VISIBLE);
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so
            // they will be looked up again once being shown.
            mAdapter.invalidateCache();
            fetchCalls();
            mRefreshDataRequired = false;
        }
    }
}
