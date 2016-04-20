/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.content.Context;
import android.net.Uri;

import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink;
import com.cyanogen.ambient.deeplink.DeepLink.DeepLinkResultList;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;

import com.android.incallui.DeepLinkIntegrationManager;

import java.util.List;
import java.util.ArrayList;

public class DeepLinkPresenter {
    CallLogListItemViewHolder views;
    Context mContext;

    public DeepLinkPresenter(CallLogListItemViewHolder holder, Context context) {
        this.views = holder;
        mContext = context;
    }

    public void handleDeepLink(List<DeepLink> links) {
        if (links != null && links.size() > 0) {
            for (DeepLink link : links) {
                if (link != null && link.getApplicationType() == DeepLinkApplicationType.NOTE
                        && link.getIcon() != DeepLink.DEFAULT_ICON) {
                    views.mDeepLink = link;
                    views.phoneCallDetailsViews.noteIconView.setVisibility(
                            android.view.View.VISIBLE);
                    views.phoneCallDetailsViews.nameWrapper.requestLayout();
                    views.phoneCallDetailsViews.noteIconView
                            .setImageDrawable(views.mDeepLink.getDrawableIcon(mContext));
                }
            }
        }
    }

    public void handleReadyForRequests(String number,
            ResultCallback<DeepLinkResultList> deepLinkCallback) {
        if (views.mDeepLink == null) {
            List<Uri> uris = buildCallUris(number);
            com.android.incallui.DeepLinkIntegrationManager.getInstance()
                    .getPreferredLinksForList(deepLinkCallback, DeepLinkContentType
                            .CALL, uris);
        } else {
            updateViews();
        }
    }

    private List<Uri> buildCallUris(String number) {
        List<Uri> uris = new ArrayList<Uri>();
        android.net.Uri toUse;
        for (int i = 0; i < views.callTimes.length; i++) {
            toUse = DeepLinkIntegrationManager.generateCallUri(number, views.callTimes[i]);
            uris.add(toUse);
        }
        return uris;
    }

    private void updateViews() {
        views.phoneCallDetailsViews.noteIconView.setVisibility(
                android.view.View.VISIBLE);
        views.phoneCallDetailsViews.nameWrapper.requestLayout();
        views.phoneCallDetailsViews.noteIconView
                .setImageDrawable(views.mDeepLink.getDrawableIcon
                        (mContext));
    }

    final ResultCallback<DeepLinkResultList> deepLinkCallback = new
            ResultCallback<DeepLinkResultList>() {
                @Override
                public void onResult(DeepLinkResultList deepLinkResult) {
                    handleDeepLink(deepLinkResult.getResults());
                }
            };

    public void prepareUi(final String number) {
        handleReadyForRequests(number, deepLinkCallback);
    }

}
