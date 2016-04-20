package com.android.dialer.calllog;

import com.cyanogen.ambient.deeplink.DeepLink;
import java.util.List;
import android.content.Context;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.deeplink.DeepLink.DeepLinkResultList;
import com.cyanogen.ambient.deeplink.linkcontent.DeepLinkContentType;
import android.net.Uri;
import java.util.List;
import java.util.ArrayList;
import com.cyanogen.ambient.deeplink.applicationtype.DeepLinkApplicationType;

public class DeepLinkAssistant {
    CallLogListItemViewHolder views;
    Context mContext;

    public DeepLinkAssistant(CallLogListItemViewHolder holder, Context context) {
        this.views = holder;
        mContext = context;
    }


    public void handleDeepLink(List<DeepLink> links) {
        if(links !=null && links.size()>0) {
            for(DeepLink link: links) {
                if(link !=null &&  link.getApplicationType() == DeepLinkApplicationType.NOTE && link
                        .getIcon()!=
                        DeepLink.DEFAULT_ICON) {
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

    public void handleReadyForRequests(String number, ResultCallback<DeepLinkResultList> deepLinkCallback) {
        if(views.mDeepLink == null) {
            List<Uri>
                    uris = buildCallUris(number);
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
            toUse = com.android.incallui.DeepLinkIntegrationManager
                    .generateCallUri(number,views.callTimes[i]);
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
            ResultCallback<DeepLinkResultList> () {
                @Override
                public void onResult(DeepLinkResultList deepLinkResult) {
                    handleDeepLink(deepLinkResult.getResults());
                }
            };

    public void prepareUi(final String number) {
        handleReadyForRequests(number, deepLinkCallback);
    }

}
