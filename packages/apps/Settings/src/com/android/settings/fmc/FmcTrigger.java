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

package com.android.settings.fmc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

// listen to the wifi state change
public class FmcTrigger extends BroadcastReceiver {

    private static final String TAG = "FmcTrigger";
    public static final String ENABLE_FMC_ACTION = "android.fmc.ENABLE_FMC_ACTION";
    public static final String FMC_ENABLED_STATUS = "fmc_enabled_status";

    private static final String[] CT_WIFI_HOTPOT = {
        "ChinaNet_HomeCW", "ChinaNet_CW", "ChinaNet"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        // check fmc enable mark first
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action="+action);
        if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            handleNetworkStateChange(intent, context);
        } else if (ENABLE_FMC_ACTION.equals(action)) {
            handleEnableFmcAction(intent, context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            android.provider.Settings.System.putInt(context.getContentResolver(),
                    Settings.System.FMC_STATUS, -1);
        }
    }

    private void handleNetworkStateChange(Intent intent, Context context) {
        boolean enabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.FMC_ENABLED, 0) == 1;
        if (!enabled)
            return;
        NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if (info == null)
            return;
        DetailedState ds = info.getDetailedState();
        if (ds!=null && ds == DetailedState.CONNECTED) {
            WifiManager mWifiManager = (WifiManager) context
                    .getSystemService(context.WIFI_SERVICE);
            final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            String ssid = (wifiInfo==null)? "" : wifiInfo.getSSID();
            Log.d(TAG, "handleNetworkStateChange: ssid="+ssid);
            if (isCTWifiHotpot(ssid)) {
                FmcOperator fmcOper = FmcOperator.getInstance(context);
                fmcOper.startFmc();
            }
        }
    }

    private void handleEnableFmcAction(Intent intent, Context context) {
        boolean isEnalbe = intent.getBooleanExtra(FMC_ENABLED_STATUS, false);
        WifiManager mWifiManager = (WifiManager) context
                .getSystemService(context.WIFI_SERVICE);
        final WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo.State networkState = (networkInfo == null ? NetworkInfo.State.UNKNOWN
                     : networkInfo.getState());
        Log.d(TAG, "handleEnableFmcAction: networkState="+networkState);
        if (networkState == NetworkInfo.State.CONNECTED) {
            String ssid = wifiInfo.getSSID();
            Log.d(TAG, "handleEnableFmcAction: ssid="+ssid);
            if (ssid!=null && isCTWifiHotpot(ssid)) {
                FmcOperator fmcOper = FmcOperator.getInstance(context);
                if (isEnalbe)
                    fmcOper.startFmc();
                else
                    fmcOper.stopFmc();
            }
        }
    }

    private boolean isCTWifiHotpot(String ssid) {
        for (int i = 0; i < CT_WIFI_HOTPOT.length; i++) {
            if (CT_WIFI_HOTPOT[i].equals(ssid))
                return true;
        }
        return false;
    }
}
