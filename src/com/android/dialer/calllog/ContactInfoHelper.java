/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.dialer.calllog;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.contacts.common.util.Constants;
import com.android.contacts.common.util.UriUtils;
import com.android.dialer.R;
import com.android.dialer.lookup.LookupCache;
import com.android.dialer.service.CachedNumberLookupService;
import com.android.dialer.service.CachedNumberLookupService.CachedContactInfo;
import com.android.dialerbind.ObjectFactory;
import com.android.i18n.phonenumbers.PhoneNumberUtil;
import com.android.internal.telephony.util.BlacklistUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Utility class to look up the contact information for a given number.
 */
public class ContactInfoHelper {
    private final Context mContext;
    private final String mCurrentCountryIso;

    private static final CachedNumberLookupService mCachedNumberLookupService =
            ObjectFactory.newCachedNumberLookupService();

    public ContactInfoHelper(Context context, String currentCountryIso) {
        mContext = context;
        mCurrentCountryIso = currentCountryIso;
    }

    /**
     * Returns the contact information for the given number.
     * <p>
     * If the number does not match any contact, returns a contact info containing only the number
     * and the formatted number.
     * <p>
     * If an error occurs during the lookup, it returns null.
     *
     * @param number the number to look up
     * @param countryIso the country associated with this number
     */
    public ContactInfo lookupNumber(String number, String countryIso) {
        final ContactInfo info;

        // Determine the contact info.
        if (PhoneNumberUtils.isUriNumber(number)) {
            // This "number" is really a SIP address.
            ContactInfo sipInfo = queryContactInfoForSipAddress(number);
            if (sipInfo == null || sipInfo == ContactInfo.EMPTY) {
                // Check whether the "username" part of the SIP address is
                // actually the phone number of a contact.
                String username = PhoneNumberUtils.getUsernameFromUriNumber(number);
                if (PhoneNumberUtils.isGlobalPhoneNumber(username)) {
                    sipInfo = queryContactInfoForPhoneNumber(username, countryIso);
                }
            }
            info = sipInfo;
        } else {
            // Look for a contact that has the given phone number.
            ContactInfo phoneInfo = queryContactInfoForPhoneNumber(number, countryIso);

            if (phoneInfo == null || phoneInfo == ContactInfo.EMPTY) {
                // Check whether the phone number has been saved as an "Internet call" number.
                phoneInfo = queryContactInfoForSipAddress(number);
            }
            info = phoneInfo;
        }

        final ContactInfo updatedInfo;
        if (info == null) {
            // The lookup failed.
            updatedInfo = null;
        } else {
            // If we did not find a matching contact, generate an empty contact info for the number.
            if (info == ContactInfo.EMPTY) {
                // Did not find a matching contact.
                updatedInfo = new ContactInfo();
                updatedInfo.number = number;
                updatedInfo.formattedNumber = formatPhoneNumber(number, null, countryIso);
                updatedInfo.lookupUri = createTemporaryContactUri(updatedInfo.formattedNumber);
            } else {
                updatedInfo = info;
            }
        }
        return updatedInfo;
    }

    /**
     * Creates a JSON-encoded lookup uri for a unknown number without an associated contact
     *
     * @param number - Unknown phone number
     * @return JSON-encoded URI that can be used to perform a lookup when clicking on the quick
     *         contact card.
     */
    private static Uri createTemporaryContactUri(String number) {
        try {
            final JSONObject contactRows = new JSONObject().put(Phone.CONTENT_ITEM_TYPE,
                    new JSONObject().put(Phone.NUMBER, number).put(Phone.TYPE, Phone.TYPE_CUSTOM));

            final String jsonString = new JSONObject().put(Contacts.DISPLAY_NAME, number)
                    .put(Contacts.DISPLAY_NAME_SOURCE, DisplayNameSources.PHONE)
                    .put(Contacts.CONTENT_ITEM_TYPE, contactRows).toString();

            return Contacts.CONTENT_LOOKUP_URI
                    .buildUpon()
                    .appendPath(Constants.LOOKUP_URI_ENCODED)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            String.valueOf(Long.MAX_VALUE))
                    .encodedFragment(jsonString)
                    .build();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Looks up a contact using the given URI.
     * <p>
     * It returns null if an error occurs, {@link ContactInfo#EMPTY} if no matching contact is
     * found, or the {@link ContactInfo} for the given contact.
     * <p>
     * The {@link ContactInfo#formattedNumber} field is always set to {@code null} in the returned
     * value.
     */
    private ContactInfo lookupContactFromUri(Uri uri) {
        final ContactInfo info;
        Cursor phonesCursor =
                mContext.getContentResolver().query(uri, PhoneQuery._PROJECTION, null, null, null);

        if (phonesCursor != null) {
            try {
                if (phonesCursor.moveToFirst()) {
                    info = new ContactInfo();
                    long contactId = phonesCursor.getLong(PhoneQuery.PERSON_ID);
                    String lookupKey = phonesCursor.getString(PhoneQuery.LOOKUP_KEY);
                    info.lookupKey = lookupKey;
                    info.lookupUri = Contacts.getLookupUri(contactId, lookupKey);
                    info.name = phonesCursor.getString(PhoneQuery.NAME);
                    info.type = phonesCursor.getInt(PhoneQuery.PHONE_TYPE);
                    info.label = phonesCursor.getString(PhoneQuery.LABEL);
                    info.number = phonesCursor.getString(PhoneQuery.MATCHED_NUMBER);
                    info.normalizedNumber = phonesCursor.getString(PhoneQuery.NORMALIZED_NUMBER);
                    info.photoId = phonesCursor.getLong(PhoneQuery.PHOTO_ID);
                    info.photoUri =
                            UriUtils.parseUriOrNull(phonesCursor.getString(PhoneQuery.PHOTO_URI));
                    info.formattedNumber = null;
                } else {
                    info = ContactInfo.EMPTY;
                }
            } finally {
                phonesCursor.close();
            }
        } else {
            // Failed to fetch the data, ignore this request.
            info = null;
        }
        return info;
    }

    /**
     * Determines the contact information for the given SIP address.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given SIP address, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForSipAddress(String sipAddress) {
        final ContactInfo info;

        // "contactNumber" is a SIP address, so use the PhoneLookup table with the SIP parameter.
        Uri.Builder uriBuilder = PhoneLookup.CONTENT_FILTER_URI.buildUpon();
        uriBuilder.appendPath(Uri.encode(sipAddress));
        uriBuilder.appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1");
        return lookupContactFromUri(uriBuilder.build());
    }

    /**
     * Determines the contact information for the given phone number.
     * <p>
     * It returns the contact info if found.
     * <p>
     * If no contact corresponds to the given phone number, returns {@link ContactInfo#EMPTY}.
     * <p>
     * If the lookup fails for some other reason, it returns null.
     */
    private ContactInfo queryContactInfoForPhoneNumber(String number, String countryIso) {
        String contactNumber = number;
        if (!TextUtils.isEmpty(countryIso)) {
            // Normalize the number: this is needed because the PhoneLookup query below does not
            // accept a country code as an input.
            String numberE164 = PhoneNumberUtils.formatNumberToE164(number, countryIso);
            if (!TextUtils.isEmpty(numberE164)) {
                // Only use it if the number could be formatted to E164.
                contactNumber = numberE164;
            }
        }

        // The "contactNumber" is a regular phone number, so use the PhoneLookup table.
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contactNumber));
        ContactInfo info = lookupContactFromUri(uri);
        if (info != null && info != ContactInfo.EMPTY) {
            info.formattedNumber = formatPhoneNumber(number, null, countryIso);
        } else if (LookupCache.hasCachedContact(mContext, number)) {
            info = LookupCache.getCachedContact(mContext, number);
        } else if (mCachedNumberLookupService != null) {
            CachedContactInfo cacheInfo =
                    mCachedNumberLookupService.lookupCachedContactFromNumber(mContext, number);
            info = cacheInfo != null ? cacheInfo.getContactInfo() : null;
        }
        return info;
    }

    /**
     * Format the given phone number
     *
     * @param number the number to be formatted.
     * @param normalizedNumber the normalized number of the given number.
     * @param countryIso the ISO 3166-1 two letters country code, the country's convention will be
     *        used to format the number if the normalized phone is null.
     *
     * @return the formatted number, or the given number if it was formatted.
     */
    private String formatPhoneNumber(String number, String normalizedNumber, String countryIso) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberUtils.isUriNumber(number)) {
            return number;
        }
        if (TextUtils.isEmpty(countryIso)) {
            countryIso = mCurrentCountryIso;
        }
        return PhoneNumberUtils.formatNumber(number, normalizedNumber, countryIso);
    }

    /**
     * Checks whether calls can be blacklisted; that is, whether the
     * phone blacklist is enabled
     */
    public boolean canBlacklistCalls() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PHONE_BLACKLIST_ENABLED, 1) != 0;
    }

    /**
     * Requests the given number to be added to the phone blacklist
     *
     * @param number the number to be blacklisted
     */
    public void addNumberToBlacklist(String number) {
        number = PhoneNumberUtil.getInstance().stripExtension(number);
        if (BlacklistUtils.addOrUpdate(mContext, number,
                BlacklistUtils.BLOCK_CALLS, BlacklistUtils.BLOCK_CALLS)) {
            // Give the user some feedback
            String message = mContext.getString(R.string.toast_added_to_blacklist, number);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Requests the given number to be added to the phone blacklist
     *
     * @param number the number to be blacklisted
     */
    public void removeNumberFromBlacklist(String number) {
        number = PhoneNumberUtil.getInstance().stripExtension(number);
        if (BlacklistUtils.addOrUpdate(mContext, number,
                BlacklistUtils.MATCH_NONE, BlacklistUtils.BLOCK_CALLS)) {
            // Give the user some feedback
            String message = mContext.getString(R.string.toast_deleted_from_blacklist, number);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Parses the given URI to determine the original lookup key of the contact.
     */
    public static String getLookupKeyFromUri(Uri lookupUri) {
        // Would be nice to be able to persist the lookup key somehow to avoid having to parse
        // the uri entirely just to retrieve the lookup key, but every uri is already parsed
        // once anyway to check if it is an encoded JSON uri, so this has negligible effect
        // on performance.
        if (lookupUri != null && !UriUtils.isEncodedContactUri(lookupUri)) {
            final List<String> segments = lookupUri.getPathSegments();
            // This returns the third path segment of the uri, where the lookup key is located.
            // See {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}.
            return (segments.size() < 3) ? null : segments.get(2);
        } else {
            return null;
        }
    }

    /**
     * Given a contact's sourceType, return true if the contact is a business
     *
     * @param sourceType sourceType of the contact. This is usually populated by
     *        {@link #mCachedNumberLookupService}.
     */
    public boolean isBusiness(int sourceType) {
        return mCachedNumberLookupService != null
                && mCachedNumberLookupService.isBusiness(sourceType);
    }
}
