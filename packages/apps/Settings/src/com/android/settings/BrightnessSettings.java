/*
* Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of Code Aurora Forum, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings;

import android.provider.Settings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

public class BrightnessSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener {
    private final static String TAG = "BrightnessSettings";
    private final static int MODE_NORMAL = 0;
    private final static int MODE_ENHANCED = 1;

    /**
     * content adaptive backlight settings
     */
    private final static String KEY_ENABLE_ENHANCED_BRIGHTNESS = "cabl_brightness";
    private final static String KEY_CHOOSE_BRIGHTNESS = "choose_brightness";
    private final static String KEY_ENABLE_NORMAL_BRIGHTNESS = "brightness";

    private static final String CABL_PACKAGE = "com.qualcomm.cabl";
    private static final String CABL_PREFS_CLASS = "com.qualcomm.cabl.CABLPreferences";
    private static final String NORMAL_SETTINGS = "Normal Brightness";
    private static final String ENHANCED_SETTINGS = "Enhanced Brightness";

    private int mMode = 0;

    ListPreference mChooseBrightnessSetting;
    BrightnessPreference mEnableNormalPreference;
    Preference mEnableEnhancedPreference;

    private boolean mCablAvailable;
    private boolean mTempDisableCabl;
    SharedPreferences mPref = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.brightness_settings);

        mPref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        /**
         * disable CABL if it is running
         */
        mCablAvailable = SystemProperties.getBoolean("ro.qualcomm.cabl", false);
        mTempDisableCabl = false;


        mChooseBrightnessSetting = (ListPreference) findPreference(KEY_CHOOSE_BRIGHTNESS);
        mEnableNormalPreference = (BrightnessPreference) findPreference(KEY_ENABLE_NORMAL_BRIGHTNESS);
        mEnableEnhancedPreference = (Preference) findPreference(KEY_ENABLE_ENHANCED_BRIGHTNESS);

        mChooseBrightnessSetting.setOnPreferenceChangeListener(this);
        mEnableEnhancedPreference.setOnPreferenceChangeListener(this);

//        mMode = (NORMAL_SETTINGS.equals(mPref.getString(KEY_CHOOSE_BRIGHTNESS, NORMAL_SETTINGS))) ? MODE_NORMAL : MODE_ENHANCED;
       //system language is chinese
        mMode = (ENHANCED_SETTINGS.equals(mPref.getString(KEY_CHOOSE_BRIGHTNESS, NORMAL_SETTINGS))) ? MODE_ENHANCED : MODE_NORMAL;

//        mMode = Settings.System.getInt(this.getContentResolver(), Settings.System.BRIGHTNESS_MODE,
//                0);
        changePrefStatus();
        Log.d(TAG, "mode =" + mMode);
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (preference == mChooseBrightnessSetting) {
            Log.d(TAG, "objValue=" + objValue.toString());
            mMode = (NORMAL_SETTINGS.equals(objValue.toString())) ? MODE_NORMAL : MODE_ENHANCED;
        }
        changePrefStatus();

      //disable CABL in MODE_NORMAL,enable CABL in MODE_ENHANCED
        if(mCablAvailable && mTempDisableCabl && mMode == MODE_ENHANCED) {
            SystemProperties.set("ctl.start", "abld");
            mTempDisableCabl = false;
        }
        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mEnableEnhancedPreference) {
            Log.d(TAG, "preference = mEnableEnhancedPreference clicked");
            Intent intent = new Intent();
            intent.setClassName(CABL_PACKAGE, CABL_PREFS_CLASS);
            startActivity(intent);
        }else if(preference == mChooseBrightnessSetting){
            Log.d(TAG, "mChooseBrightnessSetting clicked");
            if(mCablAvailable && SystemProperties.get("init.svc.abld").equals("running")) {
                SystemProperties.set("ctl.stop", "abld");
                mTempDisableCabl = true;
            }
        }
        return true;
    }

    private void changePrefStatus() {

        mEnableNormalPreference.setEnabled(mMode == MODE_NORMAL);
        mEnableEnhancedPreference.setEnabled(mMode == MODE_ENHANCED);

//        Settings.System.putInt(this.getContentResolver(), Settings.System.BRIGHTNESS_MODE, mMode);
        int strResId = (mMode == MODE_NORMAL) ? R.string.brightness : R.string.enhanced_brightness;
        mChooseBrightnessSetting.setSummary(strResId);
        mChooseBrightnessSetting.setValueIndex(mMode);

        //we need to disable auto brightness in enhanced mode
        if(mMode == MODE_ENHANCED){
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        }

    }

}
