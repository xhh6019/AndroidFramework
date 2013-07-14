/*
 *
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *     Neither the name of Code Aurora Forum, Inc. nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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

package com.android.settings.multisimsettings;

import com.android.settings.R;

import android.os.AsyncResult;
import android.os.Message;
import android.os.Handler;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.preference.Preference;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.SubscriptionManager;

import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.AirplaneModeEnabler;


public class PreferredSubscriptionListPreference extends ListPreference implements
                                           Preference.OnPreferenceChangeListener {
    private static final String LOG_TAG = "PreferredSubscriptionListPreference";
    private static final boolean DBG = true;
    private int mPreferredSubscription;

    private int mType;
    private Context mContext;

    private final LocalMultiSimSettingsManager mLocalManager;


    public PreferredSubscriptionListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mLocalManager = LocalMultiSimSettingsManager.getInstance(context);
    }

    public PreferredSubscriptionListPreference(Context context) {
        this(context, null);
    }

    public void setType(int listType) {
        mType = listType;
        mPreferredSubscription = mLocalManager.getPreferredSubscription(listType);
        setValue(String.valueOf(mPreferredSubscription));
        setEntries(mLocalManager.getMultiSimNames());
        setSummary(mLocalManager.getMultiSimName(mPreferredSubscription));
    }

    public void resume() {
        setOnPreferenceChangeListener(this);
        setType(mType);
        updatePreferenceState();
        mLocalManager.registerForSimNameChange(mHandler, MultiSimSettingsConstants.EVENT_MULTI_SIM_NAME_CHANGED, null);
    }

    public void pause() {
        setOnPreferenceChangeListener(null);
        mLocalManager.unregisterForSimNameChange(mHandler);
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        int preferredSubscription = Integer.parseInt((String) value);
        if(DBG) Log.d(LOG_TAG, "onPreferenceChange: " + preferredSubscription);

        if (preferredSubscription == mPreferredSubscription) {
            // do nothing but warning
            if (DBG) Log.d(LOG_TAG, "preferred subscription not changed");
        } else if (AirplaneModeEnabler.isAirplaneModeOn(mContext)) {
            // do nothing but warning
            if (DBG) Log.e(LOG_TAG, "error! airplane is on");
        } else {
            mLocalManager.setPreferredSubscription(Message.obtain(mHandler,
                MultiSimSettingsConstants.EVENT_PREFERRED_SUBSCRIPTION_CHANGED, mType, preferredSubscription));
        }

        // Don't update UI to opposite state until we're sure
        return false;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult)msg.obj;

            switch(msg.what) {
                case MultiSimSettingsConstants.EVENT_MULTI_SIM_NAME_CHANGED:
                    handleSimNameChanged(ar);
                    break;
                case MultiSimSettingsConstants.EVENT_PREFERRED_SUBSCRIPTION_CHANGED:
                    handlePreferredSubscriptionChanged();
                    break;
            }
        }
    };

    private void handleSimNameChanged(AsyncResult ar) {
        int subscription = ((Integer)ar.result).intValue();
        if (DBG) Log.d(LOG_TAG, "channel name changed on sub" + subscription);
        if (mPreferredSubscription == subscription) {
            setSummary(mLocalManager.getMultiSimName(mPreferredSubscription));
        }
        setEntries(mLocalManager.getMultiSimNames());
    }

    private void handlePreferredSubscriptionChanged() {
        if(DBG) Log.d(LOG_TAG, "default subscription changed in " + mType);

        int preferredSubscription = mLocalManager.getPreferredSubscription(mType);

        if (preferredSubscription == mPreferredSubscription) {
            if(DBG) Log.d(LOG_TAG, "set default subscription fails on " + mType);
        } else {
            mPreferredSubscription = preferredSubscription;
            if(DBG) Log.d(LOG_TAG, "now default subscription is : " + mPreferredSubscription + " on " + mType);
            setSummary(mLocalManager.getMultiSimName(mPreferredSubscription));
        }
        setValue(String.valueOf(preferredSubscription));
    }

    private void updatePreferenceState() {
        boolean isEnabled = false;
        boolean isOn = AirplaneModeEnabler.isAirplaneModeOn(mContext);
        if (!isOn) {
            //only not in airplane mode and the number of active subscription is 2, enabled.
            if (SubscriptionManager.getInstance().getActiveSubscriptionsCount() == 2) {
                isEnabled = true;
            }
        }

        if(DBG) Log.d(LOG_TAG, "setEnabled: " + isEnabled);
        setEnabled(isEnabled);
    }
}
