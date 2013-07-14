/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
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
import com.android.internal.telephony.cdma.TtyIntent;
import com.android.internal.telephony.SubscriptionManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import android.net.sip.SipManager;
import android.preference.PreferenceGroup;
import com.android.phone.sip.SipSharedPreferences;

import com.qrd.plugin.feature_query.FeatureQuery;

/**
 * Top level "Call settings" UI; see res/xml/call_feature_setting.xml
 *
 * This preference screen is the root of the "Call settings" hierarchy
 * available from the Phone app; the settings here let you control various
 * features related to phone calls (including voicemail settings, SIP
 * settings, the "Respond via SMS" feature, and others.)  It's used only
 * on voice-capable phone devices.
 *
 * Note that this activity is part of the package com.android.phone, even
 * though you reach it from the "Phone" app (i.e. DialtactsActivity) which
 * is from the package com.android.contacts.
 *
 * For the "Mobile network settings" screen under the main Settings app,
 * see apps/Phone/src/com/android/phone/Settings.java.
 */
public class MSimCallFeaturesSetting extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    // debug data
    private static final String LOG_TAG = "MSimCallFeaturesSetting";
    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    // String keys for preference lookup
    // TODO: Naming these "BUTTON_*" is confusing since they're not actually buttons(!)
    private static final String BUTTON_DTMF_KEY   = "button_dtmf_settings";
    private static final String BUTTON_RETRY_KEY  = "button_auto_retry_key";
    private static final String BUTTON_PROXIMITY_KEY = "button_proximity_key";    // add for new feature: proximity sensor
    private static final String BUTTON_TTY_KEY    = "button_tty_mode_key";
    private static final String BUTTON_HAC_KEY    = "button_hac_key";
    private static final String BUTTON_SELECT_SUB_KEY = "button_call_independent_serv";
    private static final String BUTTON_XDIVERT_KEY = "button_xdivert";

    private static final String DISPLAY_HOME_LOCATION_KEY   = "display_home_location_key";

    private static final String BUTTON_SIP_CALL_OPTIONS = "sip_call_options_key";
    private static final String BUTTON_SIP_CALL_OPTIONS_WIFI_ONLY = "sip_call_options_wifi_only_key";
    private static final String SIP_SETTINGS_CATEGORY_KEY = "sip_settings_category_key";

    private static final String SPEED_DIAL_SETTINGS_KEY = "speed_dial_settings";
    // preferred TTY mode
    // Phone.TTY_MODE_xxx
    static final int preferredTtyMode = Phone.TTY_MODE_OFF;

    // Dtmf tone types
    static final int DTMF_TONE_TYPE_NORMAL = 0;
    static final int DTMF_TONE_TYPE_LONG   = 1;

    private static final int SUB1 = 0;
    private static final int SUB2 = 1;

    public static final String HAC_KEY = "HACSetting";
    public static final String HAC_VAL_ON = "ON";
    public static final String HAC_VAL_OFF = "OFF";

    protected Phone mPhone;

    private AudioManager mAudioManager;

    private CheckBoxPreference mButtonAutoRetry;
    private CheckBoxPreference mButtonHAC;
    private CheckBoxPreference mButtonProximity;
    private ListPreference mButtonDTMF;
    private ListPreference mButtonTTY;
    private PreferenceScreen mButtonXDivert;
    private XDivertCheckBoxPreference mXDivertCheckbox;
    private CheckBoxPreference mDisplayHomeLocation;
    private Phone mPhoneObj[];
    private int mPhoneType[];
    private int mNumPhones;
    private String mRawNumber[];
    private String mLine1Number[];
    private boolean mIsSubActive[];

    private SubscriptionManager mSubManager;

    private SipManager mSipManager;
    private ListPreference mButtonSipCallOptions;
    private SipSharedPreferences mSipSharedPreferences;

    /*
     * Click Listeners, handle click based on objects attached to UI.
     */

    // Click listener for all toggle events
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonDTMF) {
            return true;
        } else if (preference == mButtonTTY) {
            return true;
        } else if (preference == mButtonAutoRetry) {
            android.provider.Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.System.CALL_AUTO_RETRY,
                    mButtonAutoRetry.isChecked() ? 1 : 0);
            return true;
        } else if (preference == mButtonHAC) {
            int hac = mButtonHAC.isChecked() ? 1 : 0;
            // Update HAC value in Settings database
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    Settings.System.HEARING_AID, hac);

            // Update HAC Value in AudioManager
            mAudioManager.setParameter(HAC_KEY, hac != 0 ? HAC_VAL_ON : HAC_VAL_OFF);
            return true;
        } else if (preference == mButtonXDivert) {
             preProcessXDivert();
             processXDivert();
             return true;
        } else if (preference == mButtonProximity) {
            boolean checked = mButtonProximity.isChecked();
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.System.PROXIMITY_SENSOR,
                    checked ? 1 : 0);
            mButtonProximity.setSummary(checked ? R.string.proximity_on_summary : R.string.proximity_off_summary);
            return true;
        }
        return false;
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes.
     *
     * @param preference is the preference to be changed
     * @param objValue should be the value of the selection, NOT its localized
     * display value.
     */
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mButtonDTMF) {
            int index = mButtonDTMF.findIndexOfValue((String) objValue);
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, index);
        } else if (preference == mButtonTTY) {
            handleTTYChange(preference, objValue);
        } else if (preference == mDisplayHomeLocation) {
            handleDisplayHomeLocationChange(preference, objValue);
        } else if (preference == mButtonProximity) {
            boolean checked = mButtonProximity.isChecked();
            Settings.System.putInt(mPhone.getContext().getContentResolver(),
                    android.provider.Settings.System.PROXIMITY_SENSOR, checked ? 1 : 0);
            mButtonProximity.setSummary(checked ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        } else if(preference == mButtonSipCallOptions) {
            handleSipCallOptionsChange(objValue);
        }
        // always let the preference setting proceed.
        return true;
    }

    /*
     * Activity class methods
     */

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (DBG) log("Creating activity");
        mPhone = MSimPhoneApp.getInstance().getPhone();

        addPreferencesFromResource(R.xml.msim_call_feature_setting);

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // get buttons
        PreferenceScreen prefSet = getPreferenceScreen();
        mSubManager = SubscriptionManager.getInstance();

        mButtonDTMF = (ListPreference) findPreference(BUTTON_DTMF_KEY);
        mButtonAutoRetry = (CheckBoxPreference) findPreference(BUTTON_RETRY_KEY);
        mButtonProximity = (CheckBoxPreference) findPreference(BUTTON_PROXIMITY_KEY);
        mButtonHAC = (CheckBoxPreference) findPreference(BUTTON_HAC_KEY);
        mButtonTTY = (ListPreference) findPreference(BUTTON_TTY_KEY);
        mButtonXDivert = (PreferenceScreen) findPreference(BUTTON_XDIVERT_KEY);
        mDisplayHomeLocation = (CheckBoxPreference)findPreference(DISPLAY_HOME_LOCATION_KEY);
        if (mDisplayHomeLocation != null ) {
            mDisplayHomeLocation.setPersistent(false);
            mDisplayHomeLocation.setOnPreferenceChangeListener(this);
        }
        if (mButtonDTMF != null) {
            if (getResources().getBoolean(R.bool.dtmf_type_enabled)) {
                mButtonDTMF.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(mButtonDTMF);
                mButtonDTMF = null;
            }
        }

        if (mButtonAutoRetry != null) {
            if (getResources().getBoolean(R.bool.auto_retry_enabled)) {
                mButtonAutoRetry.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(mButtonAutoRetry);
                mButtonAutoRetry = null;
            }
        }

        if (mButtonProximity != null) {
            if (true) { // TODO: need change to feature query
                mButtonProximity.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(mButtonProximity);
                mButtonProximity = null;
            }
        }

        if (mButtonHAC != null) {
            if (getResources().getBoolean(R.bool.hac_enabled)) {

                mButtonHAC.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(mButtonHAC);
                mButtonHAC = null;
            }
        }

        if (mButtonTTY != null) {
            if (getResources().getBoolean(R.bool.tty_enabled)) {
                mButtonTTY.setOnPreferenceChangeListener(this);
            } else {
                prefSet.removePreference(mButtonTTY);
                mButtonTTY = null;
            }
        }

        PreferenceScreen selectSub = (PreferenceScreen) findPreference(BUTTON_SELECT_SUB_KEY);
        if (selectSub != null) {
            Intent intent = selectSub.getIntent();
            intent.putExtra(SelectSubscription.PACKAGE, "com.android.phone");
            intent.putExtra(SelectSubscription.TARGET_CLASS,
                    "com.android.phone.MSimCallFeaturesSubSetting");
        }

        if (mButtonXDivert != null) {
            if (!getResources().getBoolean(R.bool.xdivert_enabled)) {
                prefSet.removePreference(mButtonXDivert);
                mButtonXDivert = null;
            }
        }

        if (mButtonXDivert != null) {
            mNumPhones = MSimTelephonyManager.getDefault().getPhoneCount();
            if (mNumPhones < 1) {
                prefSet.removePreference(mButtonXDivert);
                mButtonXDivert = null;
            } else {
                mButtonXDivert.setOnPreferenceChangeListener(this);
                preProcessXDivert();
            }
        }

        if (!FeatureQuery.FEATURE_CONTACTS_SPEED_DIAL) {
            PreferenceScreen spdSettings = (PreferenceScreen)findPreference(SPEED_DIAL_SETTINGS_KEY);
            prefSet.removePreference(spdSettings);
        }

       //for internet call settings
       if (!FeatureQuery.FEATURE_PHONE_RESTRICT_VOIP && PhoneUtils.isVoipSupported()) {
           initSipSettingsPref();
       } else {
           PreferenceGroup sipSettingPref = (PreferenceGroup)findPreference(SIP_SETTINGS_CATEGORY_KEY);
           prefSet.removePreference(sipSettingPref);
       }
    }

    public void preProcessXDivert() {
        mPhoneObj = new Phone[mNumPhones];
        mRawNumber = new String[mNumPhones];
        mLine1Number = new String[mNumPhones];
        mPhoneType = new int[mNumPhones];
        mIsSubActive = new boolean[mNumPhones];
        for (int i = 0; i < mNumPhones; i++) {
            mPhoneObj[i] = MSimPhoneApp.getInstance().getPhone(i);
            mRawNumber[i] = null;
            mRawNumber[i] = mPhoneObj[i].getLine1Number();
            mLine1Number[i] = null;
            if (!TextUtils.isEmpty(mRawNumber[i])) {
                mLine1Number[i] = PhoneNumberUtils.formatNumber(mRawNumber[i]);
            }
            mPhoneType[i] = mPhoneObj[i].getPhoneType();
            mIsSubActive[i] = mSubManager.isSubActive(i);
            Log.d(LOG_TAG,"phonetype = " + mPhoneType[i] + "mIsSubActive = " + mIsSubActive[i]
                    + "mLine1Number = " + mLine1Number[i]);
        }
    }

    public void processXDivert() {
        if ((mIsSubActive[SUB1] == false) || (mIsSubActive[SUB2] == false)) {
            //Is a subscription is deactived/or only one SIM is present,
            //dialog would be displayed stating the same.
            displayAlertDialog(R.string.xdivert_sub_absent);
        } else if (mPhoneType[SUB1] == Phone.PHONE_TYPE_CDMA ||
                mPhoneType[SUB2] == Phone.PHONE_TYPE_CDMA) {
            //X-Divert is not supported for CDMA phone.Hence for C+G / C+C,
            //dialog would be displayed stating the same.
            displayAlertDialog(R.string.xdivert_not_supported);
        } else if ((mLine1Number[SUB1] == null) || (mLine1Number[SUB2] == null)) {
            //SIM records does not have msisdn, hence ask user to enter
            //the phone numbers.
            Intent intent = new Intent();
            intent.setClass(this, XDivertPhoneNumbers.class);
            startActivity(intent);
        } else {
            //SIM records have msisdn.Hence directly process
            //XDivert feature
            processXDivertCheckBox();
        }
    }

    public void displayAlertDialog(int resId) {
        new AlertDialog.Builder(this).setMessage(resId)
            .setTitle(R.string.xdivert_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d(LOG_TAG, "X-Divert onClick");
                    }
                })
            .show()
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        Log.d(LOG_TAG, "X-Divert onDismiss");
                    }
            });
    }

    public void processXDivertCheckBox() {
        Log.d(LOG_TAG,"processXDivertCheckBox line1 = " + mLine1Number[SUB1] +
            "line2 = " + mLine1Number[SUB2]);
        Intent intent = new Intent();
        intent.setClass(this, XDivertSetting.class);
        intent.putExtra("Sub1_Line1Number" ,mLine1Number[SUB1]);
        intent.putExtra("Sub2_Line1Number" ,mLine1Number[SUB2]);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mButtonDTMF != null) {
            int dtmf = Settings.System.getInt(getContentResolver(),
                    Settings.System.DTMF_TONE_TYPE_WHEN_DIALING, DTMF_TONE_TYPE_NORMAL);
            mButtonDTMF.setValueIndex(dtmf);
        }

        if (mButtonAutoRetry != null) {
            int autoretry = Settings.System.getInt(getContentResolver(),
                    Settings.System.CALL_AUTO_RETRY, 0);
            mButtonAutoRetry.setChecked(autoretry != 0);
        }

        if (mButtonProximity != null) {
            int proximity = Settings.System.getInt(getContentResolver(), Settings.System.PROXIMITY_SENSOR, 1);
            boolean checked = (proximity == 1);
            mButtonProximity.setChecked(checked);
            mButtonProximity.setSummary(checked ? R.string.proximity_on_summary : R.string.proximity_off_summary);
        }

        if (mButtonHAC != null) {
            int hac = Settings.System.getInt(getContentResolver(), Settings.System.HEARING_AID, 0);
            mButtonHAC.setChecked(hac != 0);
        }

        if (mButtonTTY != null) {
            int settingsTtyMode = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.PREFERRED_TTY_MODE,
                    Phone.TTY_MODE_OFF);
            mButtonTTY.setValue(Integer.toString(settingsTtyMode));
            updatePreferredTtyModeSummary(settingsTtyMode);
        }

        if (mDisplayHomeLocation != null) {
            updateHomeLocationCheckbox();
        }
    }

    private void updateHomeLocationCheckbox() {
        log("updateHomeLocationCheckbox() should check " + ((Settings.System.getInt(
                getContentResolver(),
                Settings.System.DISPLAY_HOME_LOCATION, 1) != 0 )? "true" : "false"));
        mDisplayHomeLocation.setChecked(Settings.System.getInt(
                getContentResolver(),
                Settings.System.DISPLAY_HOME_LOCATION, 1) != 0);
    }

    private void handleTTYChange(Preference preference, Object objValue) {
        int buttonTtyMode;
        buttonTtyMode = Integer.valueOf((String) objValue).intValue();
        int settingsTtyMode = android.provider.Settings.Secure.getInt(
                getContentResolver(),
                android.provider.Settings.Secure.PREFERRED_TTY_MODE, preferredTtyMode);
        if (DBG) log("handleTTYChange: requesting set TTY mode enable (TTY) to" +
                Integer.toString(buttonTtyMode));

        if (buttonTtyMode != settingsTtyMode) {
            switch(buttonTtyMode) {
            case Phone.TTY_MODE_OFF:
            case Phone.TTY_MODE_FULL:
            case Phone.TTY_MODE_HCO:
            case Phone.TTY_MODE_VCO:
                android.provider.Settings.Secure.putInt(getContentResolver(),
                        android.provider.Settings.Secure.PREFERRED_TTY_MODE, buttonTtyMode);
                break;
            default:
                buttonTtyMode = Phone.TTY_MODE_OFF;
            }

            mButtonTTY.setValue(Integer.toString(buttonTtyMode));
            updatePreferredTtyModeSummary(buttonTtyMode);
            Intent ttyModeChanged = new Intent(TtyIntent.TTY_PREFERRED_MODE_CHANGE_ACTION);
            ttyModeChanged.putExtra(TtyIntent.TTY_PREFFERED_MODE, buttonTtyMode);
            sendBroadcast(ttyModeChanged);
        }
    }

    private void handleDisplayHomeLocationChange(Preference preference,Object objValue) {
        boolean isEnabled = Boolean.parseBoolean(objValue.toString());
        log("handleDisplayHomeLocationChange() display is enabled " + (isEnabled? "true" : "false"));
        android.provider.Settings.System.putInt(getContentResolver(),
                Settings.System.DISPLAY_HOME_LOCATION, isEnabled ? 1 : 0);

    }

    private void updatePreferredTtyModeSummary(int TtyMode) {
        String [] txts = getResources().getStringArray(R.array.tty_mode_entries);
        switch(TtyMode) {
            case Phone.TTY_MODE_OFF:
            case Phone.TTY_MODE_HCO:
            case Phone.TTY_MODE_VCO:
            case Phone.TTY_MODE_FULL:
                mButtonTTY.setSummary(txts[TtyMode]);
                break;
            default:
                mButtonTTY.setEnabled(false);
                mButtonTTY.setSummary(txts[Phone.TTY_MODE_OFF]);
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private void initSipSettingsPref() {
        mSipManager = SipManager.newInstance(this);
        mSipSharedPreferences = new SipSharedPreferences(this);
        mButtonSipCallOptions = getSipCallOptionPreference();
        mButtonSipCallOptions.setOnPreferenceChangeListener(this);
        mButtonSipCallOptions.setValueIndex(
             mButtonSipCallOptions.findIndexOfValue(
                    mSipSharedPreferences.getSipCallOption()));
        mButtonSipCallOptions.setSummary(mButtonSipCallOptions.getEntry());

    }

    // Gets the call options for SIP depending on whether SIP is allowed only
    // on Wi-Fi only; also make the other options preference invisible.
    private ListPreference getSipCallOptionPreference() {
        ListPreference wifiAnd3G = (ListPreference)findPreference(BUTTON_SIP_CALL_OPTIONS);
        ListPreference wifiOnly = (ListPreference)findPreference(BUTTON_SIP_CALL_OPTIONS_WIFI_ONLY);
        PreferenceGroup sipSettings = (PreferenceGroup)findPreference(SIP_SETTINGS_CATEGORY_KEY);
        if (SipManager.isSipWifiOnly(this)) {
            sipSettings.removePreference(wifiAnd3G);
            return wifiOnly;
        } else {
            sipSettings.removePreference(wifiOnly);
            return wifiAnd3G;
        }
    }

    private void handleSipCallOptionsChange(Object objValue) {
        String option = objValue.toString();
        mSipSharedPreferences.setSipCallOption(option);
        mButtonSipCallOptions.setValueIndex(
                mButtonSipCallOptions.findIndexOfValue(option));
        mButtonSipCallOptions.setSummary(mButtonSipCallOptions.getEntry());
    }

}
