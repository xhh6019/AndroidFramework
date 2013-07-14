/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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
package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.FmcNotifier;
import android.net.FmcProvider;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.content.Context;
import android.preference.Preference;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;


public class FmcEnabler implements Preference.OnPreferenceChangeListener{

    public final static String TAG = "FmcEnabler";
    public static final String ENABLE_FMC_ACTION = "android.fmc.ENABLE_FMC_ACTION";
    public static final String FMC_STATE_CHANGED_ACTION = "android.fmc.FMC_STATE_CHANGED_ACTION";
    public static final String FMC_ENABLED_STATUS = "fmc_enabled_status";

    private static final String[] CT_WIFI_HOTPOT = {
        "ChinaNet_HomeCW", "ChinaNet_CW", "ChinaNet"
    };

    private static final int RESUME_PREFERENCE = 1;
    private static final long DELAY_TIME = 10*1000L;

    private static final long DELAY_START_TIME = 15000L;
    private static final long DELAY_STOP_TIME =  30000L;

    // Internal state
    private static final int NOT_START = 0;
    private static final int STARTING = 1;
    private static final int STOPPING = 2;

    private final Context mContext;
    private final CheckBoxPreference mCheckBox;

    private int mState = NOT_START;
    private long mStartTime = -1;

    // for stopFmc, we need to wait until cne return a state.
    private BroadcastReceiver fmcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (FMC_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int status = intent.getIntExtra("FmcStatus", -1);
                Log.d(TAG, "fmcReceiver status =" + status);

                switch (status) {
                // for start fmc
                case FmcNotifier.FMC_STATUS_ENABLED:
                case FmcNotifier.FMC_STATUS_REGISTRATION_SUCCESS:
                case FmcNotifier.FMC_STATUS_DS_NOT_AVAIL:
                case FmcNotifier.FMC_STATUS_CLOSED:
                case FmcNotifier.FMC_STATUS_NOT_YET_STARTED:
                case FmcNotifier.FMC_STATUS_FAILURE:
                    myHandler.removeMessages(RESUME_PREFERENCE);
                    mState = NOT_START;
                    myHandler.obtainMessage(RESUME_PREFERENCE).sendToTarget();
                    break;

                default:
                    break;
                }
            }
        }
    };

    private IntentFilter fmcFilter = new IntentFilter(FMC_STATE_CHANGED_ACTION);

    public FmcEnabler(Context context, CheckBoxPreference checkBox) {
        mContext = context;
        mCheckBox = checkBox;
        mCheckBox.setOnPreferenceChangeListener(this);

        // need to restore previous state?
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        mState = sp.getInt("state", NOT_START);
        mStartTime = sp.getLong("start_time", -1);
        Log.d(TAG, "new FmcEnabler state="+mState+" startTime:"+mStartTime);
    }

    public void resume() {
        if (mCheckBox != null) {
            mContext.registerReceiver(fmcReceiver, fmcFilter);

            boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.FMC_ENABLED, 0) == 1;
            mCheckBox.setChecked(enabled);
            mCheckBox.setSummary(enabled? R.string.fmc_switch_summary_on :
                R.string.fmc_switch_summary_off);
            Log.d(TAG, "resume: enable="+enabled);
            resumePreviousState();
        }
    }

    public void pause() {
        if (mCheckBox != null) {
            mContext.unregisterReceiver(fmcReceiver);
        }
    }

     public boolean onPreferenceChange(Preference preference, Object objValue) {
         boolean isEnabled = Boolean.parseBoolean(objValue.toString());
         Log.d(TAG,"onPreferenceChange enable="+isEnabled);
         android.provider.Settings.System.putInt(mContext.getContentResolver(),
                 Settings.System.FMC_ENABLED, isEnabled ? 1 : 0);
         mCheckBox.setSummary(isEnabled? R.string.fmc_switch_summary_on :
             R.string.fmc_switch_summary_off);

         // notify the enable/disable event
         Intent intent = new Intent(ENABLE_FMC_ACTION);
         intent.putExtra(FMC_ENABLED_STATUS, isEnabled);
         mContext.sendBroadcast(intent);

         // just return if no need to disable UI
         if (!needDisableUI())
             return true;

         // set internal state
         mState = isEnabled ? STARTING : STOPPING;

         // we will disable user action to operate FMC service for 10 seconds
         // to ensure FMC state machine work normally
         mCheckBox.setEnabled(false);
         mCheckBox.setSummary(isEnabled? R.string.fmc_switching_summary_on :
             R.string.fmc_switching_summary_off);
         long timeout = isEnabled ? DELAY_START_TIME : DELAY_STOP_TIME;
         mStartTime = System.currentTimeMillis();
         myHandler.sendEmptyMessageDelayed(RESUME_PREFERENCE, timeout);
         return true;
     }

     public void saveInstanceState() {
         Log.d(TAG,"saveInstanceState: mState="+mState+" mStartTime:"+mStartTime);
         SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
         SharedPreferences.Editor editor = sp.edit();
         editor.putInt("state", mState);
         if (mStartTime != -1)
             editor.putLong("start_time", mStartTime);
         editor.commit();

         // before destroyed, we need to remove all the pending message
         myHandler.removeMessages(RESUME_PREFERENCE);
     }

    private void resumePreviousState() {

        int status = android.provider.Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.FMC_STATUS, -1);

        Log.d(TAG, "resumePreviousState preState:" + mState + " mStartTime:"
                + mStartTime + "FMC status=" + status);

        if (mState == NOT_START)
            return;

        // just return if no need to disable UI
        if (!needDisableUI())
            return;

        if (status == FmcNotifier.FMC_STATUS_ENABLED
                || status == FmcNotifier.FMC_STATUS_REGISTRATION_SUCCESS
                || status == FmcNotifier.FMC_STATUS_DS_NOT_AVAIL
                || status == FmcNotifier.FMC_STATUS_CLOSED
                || status == FmcNotifier.FMC_STATUS_NOT_YET_STARTED
                || status == FmcNotifier.FMC_STATUS_FAILURE || status == -1) {
            myHandler.removeMessages(RESUME_PREFERENCE);
            mState = NOT_START;
            myHandler.obtainMessage(RESUME_PREFERENCE).sendToTarget();
            return;
        }

        long maxTime = (mState == STARTING) ? DELAY_START_TIME
                : DELAY_STOP_TIME;
        long passTime = System.currentTimeMillis() - mStartTime;

        if (passTime <= 0 || passTime >= maxTime) {
            mState = NOT_START;
            mStartTime = -1;
            return;
        }

        long remainTime = maxTime - passTime;
        Log.d(TAG, "resumePreviousState mState:" + mState + " remainTime:"
                + remainTime);
        // disable user input
        boolean isEnabled = Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.FMC_ENABLED, 0) == 1;
        mCheckBox.setEnabled(false);
        mCheckBox.setSummary(isEnabled ? R.string.fmc_switching_summary_on
                : R.string.fmc_switching_summary_off);
        myHandler.removeMessages(RESUME_PREFERENCE);
        myHandler.sendEmptyMessageDelayed(RESUME_PREFERENCE, remainTime);
    }

    private boolean needDisableUI() {
        WifiManager mWifiManager = (WifiManager) mContext
                .getSystemService(Context.WIFI_SERVICE);
        final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        ConnectivityManager cm = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo.State networkState = (networkInfo == null ? NetworkInfo.State.UNKNOWN
                : networkInfo.getState());
        if (networkState == NetworkInfo.State.CONNECTED) {
            String ssid = wifiInfo.getSSID();
            if (ssid!=null && isCTWifiHotpot(ssid)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCTWifiHotpot(String ssid) {
        for (int i = 0; i < CT_WIFI_HOTPOT.length; i++) {
            if (CT_WIFI_HOTPOT[i].equals(ssid))
                return true;
        }
        return false;
    }

     private Handler myHandler = new Handler() {
         @Override
         public void handleMessage(Message msg) {
             switch(msg.what) {
                 case RESUME_PREFERENCE:
                     Log.d(TAG,"handleMessage RESUME_PREFERENCE");
                     boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                             Settings.System.FMC_ENABLED, 0) == 1;
                     mCheckBox.setEnabled(true);
                     mCheckBox.setSummary(enabled? R.string.fmc_switch_summary_on :
                              R.string.fmc_switch_summary_off);
                     mState = NOT_START;
                     break;
                 default:
                     break;
             }
         }
     };
}
