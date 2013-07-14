/*
 * Copyright (C) 2009 The Android Open Source Project
 * Copyright (c) 2010-2012, Code Aurora Forum. All rights reserved.
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

package com.android.mms.ui;

import com.android.mms.R;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.PreferenceActivity;

import android.util.Log;

import com.android.internal.telephony.MSimConstants;

public class SelectSubscription extends PreferenceActivity {
    // debug data
    private static final String LOG_TAG = "SelectSubscription";

    // String keys for preference lookup
    private static final String KEY_SUBSCRIPTION1 = "pref_key_subscription1";
    private static final String KEY_SUBSCRIPTION2 = "pref_key_subscription2";
    public static final String PACKAGE = "PACKAGE";
    public static final String TARGET_CLASS = "TARGET_CLASS";

    // Preference instance variables.
    private Preference mSubscription1Pref;
    private Preference mSubscription2Pref;

    // Instance variables
    boolean mAreaInfoEnabled = false;
    private int mSubscription = 0;
    private String mPkg;
    private String mTargetClass;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(mPkg, mTargetClass);
        if (preference == mSubscription1Pref) {
            Log.d(LOG_TAG, "onPreferenceTreeClick: Selected Subsciption 1");
            intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, MSimConstants.SUB1);
        } else {
            Log.d(LOG_TAG, "onPreferenceTreeClick: Selected Subsciption 2");
            intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, MSimConstants.SUB2);
        }
        startActivity(intent);

        return true;
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Log.d (LOG_TAG, "On Create()");
        addPreferencesFromResource(R.xml.select_subscription);
        mSubscription1Pref = findPreference(KEY_SUBSCRIPTION1);
        mSubscription2Pref = findPreference(KEY_SUBSCRIPTION2);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent =  getIntent();
        mPkg = intent.getStringExtra(PACKAGE);
        mTargetClass = intent.getStringExtra(TARGET_CLASS);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
