/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.CommandsInterface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CdmaCallOptions extends PreferenceActivity {
    private static final String LOG_TAG = "CdmaCallOptions";
    private final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    public static final String CDMA_SUPP_CALL = "Cdma_Supp";


    private static final String BUTTON_CF_EXPAND_KEY = "button_cf_expand_key";
    private static final String BUTTON_CW_ACT_KEY = "button_cw_act_key";
    private static final String BUTTON_CW_DEACT_KEY = "button_cw_deact_key";
    private static final String BUTTON_VP_KEY = "button_voice_privacy_key";

    private static final String PARENT_KEY = "cdma_call_privacy";


    private PreferenceScreen prefCFExpand;
    private PreferenceScreen prefCWAct;
    private PreferenceScreen prefCWDeact;
    private CdmaVoicePrivacyCheckBoxPreference mButtonVoicePrivacy;

    private CdmaCallOptionsSetting mCallOptionSettings;

    IntentFilter mFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    private BroadcastReceiver mAirplaneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                boolean mode = intent.getBooleanExtra("state", false);
                getPreferenceScreen().setEnabled(!mode);
            }
        }
    };


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.cdma_call_privacy);

        // getting selected subscription
        int subscription = getIntent().getIntExtra(CallFeaturesSetting.SUBSCRIPTION_ID, 0);

        Log.d(LOG_TAG, "Getting CDMACallOptions subscription =" + subscription);
        Phone phone = PhoneApp.getInstance().getPhone();

        prefCFExpand  = (PreferenceScreen) findPreference(BUTTON_CF_EXPAND_KEY);
        prefCFExpand.getIntent().putExtra(CallFeaturesSetting.SUBSCRIPTION_ID, subscription);

        initCallWaitingPref(subscription);

        if (phone.getPhoneType() != Phone.PHONE_TYPE_CDMA
                 || getResources().getBoolean(R.bool.config_voice_privacy_disable)) {
             //disable the entire screen
             getPreferenceScreen().setEnabled(false);
         }
     }

    public void onResume() {
        super.onResume();
        registerReceiver(mAirplaneReceiver, mFilter);
        boolean airplane = (System.getInt(getContentResolver(), System.AIRPLANE_MODE_ON, 0) != 0);
        getPreferenceScreen().setEnabled(!airplane);
    }

    public void onPause() {
        super.onPause();
        unregisterReceiver(mAirplaneReceiver);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference.getKey().equals(BUTTON_VP_KEY)) {
            return true;
        }
        return false;
    }

    private void initCallWaitingPref(int subscription) {
        prefCWAct = (PreferenceScreen)findPreference(BUTTON_CW_ACT_KEY);
        prefCWDeact = (PreferenceScreen)findPreference(BUTTON_CW_DEACT_KEY);

        mCallOptionSettings = new CdmaCallOptionsSetting(this, CommandsInterface.CALL_WAITING, subscription);
	
        prefCWAct.getIntent().putExtra(CallFeaturesSetting.SUBSCRIPTION_ID, subscription)
                             .putExtra(CDMA_SUPP_CALL, true)
                             .setData(Uri.fromParts("tel", mCallOptionSettings.getActivateNumber(), null));
        prefCWAct.setSummary(mCallOptionSettings.getActivateNumber());
 		Log.d(LOG_TAG, "mCallOptionSettings.getActivateNumber() = " + mCallOptionSettings.getActivateNumber());
        prefCWDeact.getIntent().putExtra(CallFeaturesSetting.SUBSCRIPTION_ID, subscription)
                               .putExtra(CDMA_SUPP_CALL, true)
                               .setData(Uri.fromParts("tel", mCallOptionSettings.getDeactivateNumber(), null));
        prefCWDeact.setSummary(mCallOptionSettings.getDeactivateNumber());
		 Log.d(LOG_TAG, "mCallOptionSettings.getDeactivateNumber() = " + mCallOptionSettings.getDeactivateNumber());

    }
}