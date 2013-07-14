/*
 * Copyright (C) 2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.mms.transaction;

import com.android.mms.R;
import com.android.mms.ui.ManageSimMessages;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;


/**
 * Receive Intent.SIM_FULL_ACTION.  Handle notification that SIM is full.
 */
public class SimFullReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Settings.Secure.getInt(context.getContentResolver(),
            Settings.Secure.DEVICE_PROVISIONED, 0) == 1 &&
            Telephony.Sms.Intents.SIM_FULL_ACTION.equals(intent.getAction())) {

            NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

            Intent viewSimIntent = new Intent();
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, viewSimIntent, 0);

            Notification notification = new Notification();
            notification.icon = R.drawable.stat_sys_no_sim;
            notification.tickerText = setSimUimString(context.getString(R.string.sim_full_title));
            notification.defaults = Notification.DEFAULT_ALL;

            notification.setLatestEventInfo(
                    context,setSimUimString(context.getString(R.string.sim_full_title)),
                    context.getString(R.string.sim_full_body),
                    pendingIntent);
            nm.notify(ManageSimMessages.SIM_FULL_NOTIFICATION_ID, notification);
       }
    }
    private String setSimUimString(String SimString){
		if(SimString.contains("SIM")){
			String[] SimUimString = SimString.split("SIM");
			if(TelephonyManager.getDefault().isMultiSimEnabled()){
			return SimUimString[0]+"SIM/UIM"+SimUimString[1]  ;
			}else{
			return SimUimString[0]+"UIM"+SimUimString[1]  ;
			}
		}else{
		return SimString;
			}
    	}

}