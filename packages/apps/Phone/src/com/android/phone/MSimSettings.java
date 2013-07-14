/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2011-2012 Code Aurora Forum.All rights reserved.
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ThrottleManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;

/**
 * List of Phone-specific settings screens.
 */
public class MSimSettings extends PreferenceActivity implements DialogInterface.OnClickListener,
        DialogInterface.OnDismissListener {

    // debug data
    private static final String LOG_TAG = "MSimSettings";
    private static final boolean DBG = true;

    //String keys for preference lookup
    private static final String BUTTON_MANAGE_SUB_KEY = "button_settings_manage_sub";
    private static final String BUTTON_DATA_ENABLED_KEY = "button_data_enabled_key";
    private static final String BUTTON_DATA_USAGE_KEY = "button_data_usage_key";
    private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
    private static final String BUTTON_CDMA_LTE_DATA_SERVICE_KEY = "cdma_lte_data_service_key";

    //UI objects
    private CheckBoxPreference mButtonDataRoam;
    private CheckBoxPreference mButtonDataEnabled;
	//modify xhh 2013-4-28 for don't display "More->Mobile networks->Set up data service" 
    //private Preference mLteDataServicePref;
    private Preference mButtonDataUsage;
    private DataUsageListener mDataUsageListener;

    private Phone mPhone;
    private boolean mOkClicked;

    //This is a method implemented for DialogInterface.OnClickListener.
    //  Used to dismiss the dialogs when they come up.
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON1) {
            log("onClick setDataRoamingEnabled");
            mPhone.setDataRoamingEnabled(true);
            mOkClicked = true;
        } else {
            // Reset the toggle
            mButtonDataRoam.setChecked(false);
        }
    }

    public void onDismiss(DialogInterface dialog) {
        // Assuming that onClick gets called first
        if (!mOkClicked) {
            mButtonDataRoam.setChecked(false);
        }
    }

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonDataRoam) {
            if (DBG) log("onPreferenceTreeClick: preference = mButtonDataRoam");

            //normally called on the toggle click
            if (mButtonDataRoam.isChecked()) {
                // First confirm with a warning dialog about charges
                mOkClicked = false;
                new AlertDialog.Builder(this).setMessage(
                        getResources().getString(R.string.roaming_warning))
                        .setTitle(android.R.string.dialog_alert_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, this)
                        .setNegativeButton(android.R.string.no, this)
                        .show()
                        .setOnDismissListener(this);
            } else {
                mPhone.setDataRoamingEnabled(false);
            }
            return true;
        } else if (preference == mButtonDataEnabled) {
            if (DBG) log("onPreferenceTreeClick: preference == mButtonDataEnabled.");
            ConnectivityManager cm =
                    (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

            cm.setMobileDataEnabled(mButtonDataEnabled.isChecked());
            return true;
        } else 
        //modify xhh 2013-4-28 for don't display "More->Mobile networks->Set up data service" 
/*        if (preference == mLteDataServicePref) {
            String tmpl = android.provider.Settings.Secure.getString(getContentResolver(),
                        android.provider.Settings.Secure.SETUP_PREPAID_DATA_SERVICE_URL);
            if (!TextUtils.isEmpty(tmpl)) {
                TelephonyManager tm = (TelephonyManager) getSystemService(
                        Context.TELEPHONY_SERVICE);
                String imsi = tm.getSubscriberId();
                if (imsi == null) {
                    imsi = "";
                }
                final String url = TextUtils.isEmpty(tmpl) ? null
                        : TextUtils.expandTemplate(tmpl, imsi).toString();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } else {
                android.util.Log.e(LOG_TAG, "Missing SETUP_PREPAID_DATA_SERVICE_URL");
            }
            return true;
        } else */{
            // if the button is anything but the simple toggle preference,
            // we'll need to disable all preferences to reject all click
            // events until the sub-activity's UI comes up.
            preferenceScreen.setEnabled(false);
            // Let the intents be launched by the Preference manager
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (DBG) log("onCreate");

        addPreferencesFromResource(R.xml.msim_mobile_network_setting);

        mPhone = PhoneApp.getInstance().getDefaultPhone();

        //get UI object references
        PreferenceScreen prefSet = getPreferenceScreen();


        mButtonDataEnabled = (CheckBoxPreference) prefSet.findPreference(BUTTON_DATA_ENABLED_KEY);
        mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference(BUTTON_ROAMING_KEY);
	//modify xhh 2013-4-28 for don't display "More->Mobile networks->Set up data service" 
        //mLteDataServicePref = prefSet.findPreference(BUTTON_CDMA_LTE_DATA_SERVICE_KEY);
	//zhanghuian modify this code in order to don't display "yi don liu liang shi yon qing kuang"
       // mButtonDataUsage = prefSet.findPreference(BUTTON_DATA_USAGE_KEY);

        boolean isLteOnCdma = mPhone.getLteOnCdmaMode() == Phone.LTE_ON_CDMA_TRUE;

        PreferenceScreen manageSub = (PreferenceScreen) prefSet.findPreference(BUTTON_MANAGE_SUB_KEY);
        if (manageSub != null) {
            Intent intent = manageSub.getIntent();
            intent.putExtra(SelectSubscription.PACKAGE, "com.android.phone");
            intent.putExtra(SelectSubscription.TARGET_CLASS, "com.android.phone.MSimNetworkSettings");
        }

        final boolean missingDataServiceUrl = TextUtils.isEmpty(
                android.provider.Settings.Secure.getString(getContentResolver(),
                        android.provider.Settings.Secure.SETUP_PREPAID_DATA_SERVICE_URL));
        if (!isLteOnCdma || missingDataServiceUrl) {
			//zhanghuian modify this code in order to don't display "yi don liu liang shi yon qing kuang"
           // PreferenceGroup dataGroup = (PreferenceGroup)findPreference("category_data_settings");
         

          //  dataGroup.removePreference(mLteDataServicePref);
        } else {
            android.util.Log.d(LOG_TAG, "keep ltePref");
        }

        ThrottleManager tm = (ThrottleManager) getSystemService(Context.THROTTLE_SERVICE);
		//zhanghuian modify this code in order to don't display "yi don liu liang shi yon qing kuang"
      //  mDataUsageListener = new DataUsageListener(this, mButtonDataUsage, prefSet);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // upon resumption from the sub-activity, make sure we re-enable the
        // preferences.
        getPreferenceScreen().setEnabled(true);

        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        mButtonDataEnabled.setChecked(cm.getMobileDataEnabled());
        // Set UI state in onResume because a user could go home, launch some
        // app to change this setting's backend, and re-launch this settings app
        // and the UI state would be inconsistent with actual state
        mButtonDataRoam.setChecked(mPhone.getDataRoamingEnabled());
     //   mDataUsageListener.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
		//zhanghuian modify this code in order to don't display "yi don liu liang shi yon qing kuang"
     //   mDataUsageListener.pause();
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
