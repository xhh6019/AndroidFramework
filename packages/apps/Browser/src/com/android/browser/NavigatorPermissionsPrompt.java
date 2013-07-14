/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.browser;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.GeolocationPermissions;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import java.util.Vector;
import org.json.*;

public class NavigatorPermissionsPrompt extends LinearLayout {
    private LinearLayout mInner;
    private TextView mMessage;
    private Button mShareButton;
    private Button mDontShareButton;
    private CheckBox mRemember;
    private GeolocationPermissions.Callback mCallback;
    private String mAppId;
    private Vector<String> mFeatures;

    public NavigatorPermissionsPrompt(Context context) {
        this(context, null);
    }

    public NavigatorPermissionsPrompt(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void init() {
        mInner = (LinearLayout) findViewById(R.id.inner);
        mMessage = (TextView) findViewById(R.id.message);
        mShareButton = (Button) findViewById(R.id.share_button);
        mDontShareButton = (Button) findViewById(R.id.dont_share_button);
        mRemember = (CheckBox) findViewById(R.id.remember);

        final NavigatorPermissionsPrompt me = this;
        mShareButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                me.handleButtonClick(true);
            }
        });
        mDontShareButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                me.handleButtonClick(false);
            }
        });
    }

    /**
     * Shows the prompt for the given origin. When the user clicks on one of
     * the buttons, the supplied callback is be called.
     */
    public void show(Vector<String> features, String appid, GeolocationPermissions.Callback callback) {
        mAppId = appid;
        mFeatures = features;
        mCallback = callback;
        Uri uri = Uri.parse(mAppId);
        String message = "http".equals(uri.getScheme()) ?  mAppId.substring(7) : mAppId;
        String featureMsg ="";
        int i;
        for(i=0; i<features.size()-1; i++)
            featureMsg = featureMsg + features.get(i) + ", ";
        featureMsg = featureMsg + features.get(i);
        setMessage("http".equals(uri.getScheme()) ?  mAppId.substring(7) : mAppId, featureMsg.toString());
        // The checkbox should always be intially checked.
        mRemember.setChecked(true);
        showDialog(true);
    }

    /**
     * Hides the prompt.
     */
    public void hide() {
        showDialog(false);
    }

    /**
     * Handles a click on one the buttons by invoking the callback.
     */
    private void handleButtonClick(boolean allow) {
        showDialog(false);

        boolean remember = mRemember.isChecked();
        //mCallback.invoke(mFeatures,mAppId, allow, remember);
        // Convert features & appid into JSON string and pass as appid
        try {
            JSONArray tArr = new JSONArray();
            for(int i=0; i<mFeatures.size(); i++)
                tArr.put(mFeatures.get(i));

            JSONObject tJson = new JSONObject();
            tJson.put("appid", mAppId);
            tJson.put("features", tArr);

            mCallback.invoke(tJson.toString(), allow, remember);
        } catch(org.json.JSONException e) {
            mCallback.invoke(mAppId, allow, remember);
        }
    }

    /**
     * Sets the prompt's message.
     */
    private void setMessage(CharSequence origin, CharSequence feature) {
        Log.v("NavigatorPrompt", getResources().getString(R.string.navigator_permissions_prompt_message).toString());
        mMessage.setText(String.format(
            getResources().getString(R.string.navigator_permissions_prompt_message),
            origin,feature));
    }

    /**
     * Shows or hides the prompt.
     */
    private void showDialog(boolean shown) {
        mInner.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
}
