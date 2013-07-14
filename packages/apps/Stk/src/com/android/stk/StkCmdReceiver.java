/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2011 Code Aurora Forum. All rights reserved.
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

package com.android.stk;

import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.IccRefreshResponse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import static com.android.internal.telephony.cat.CatCmdMessage.SetupEventListConstants.*;
import static com.android.internal.telephony.cat.CatCmdMessage.BrowserTerminationCauses.*;

/**
 * Receiver class to get STK intents, broadcasted by telephony layer.
 *
 */
public class StkCmdReceiver extends BroadcastReceiver {
    private boolean mScreenIdle = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(AppInterface.CAT_CMD_ACTION)) {
            handleCommandMessage(context, intent);
        } else if (action.equals(AppInterface.CAT_SESSION_END_ACTION)) {
            handleSessionEnd(context, intent);
        } else if (action.equals(AppInterface.CAT_ALPHA_NOTIFY_ACTION)) {
            handleAlphaNotify(context, intent);
        } else if (action.equals(AppInterface.CAT_IDLE_SCREEN_ACTION)) {
            mScreenIdle = intent.getBooleanExtra("SCREEN_IDLE",true);
            handleScreenStatus(context);
        } else if (action.equals(Intent.ACTION_LOCALE_CHANGED)) {
            handleLocaleChange(context);
        } else if (action.equals(AppInterface.CAT_ICC_STATUS_CHANGE)) {
            handleCardStatusChange(context, intent);
        }
    }

    private void handleCommandMessage(Context context, Intent intent) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_CMD);
        args.putParcelable(StkAppService.CMD_MSG, intent
                .getParcelableExtra("STK CMD"));
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleSessionEnd(Context context, Intent intent) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_END_SESSION);
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleAlphaNotify(Context context, Intent intent) {
        Bundle args = new Bundle();
        String alphaString = intent.getStringExtra(AppInterface.ALPHA_STRING);
        args.putInt(StkAppService.OPCODE, StkAppService.OP_ALPHA_NOTIFY);
        args.putString(AppInterface.ALPHA_STRING, alphaString);
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
    }

    private void handleScreenStatus(Context context) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_IDLE_SCREEN);
        args.putBoolean(StkAppService.SCREEN_STATUS,  mScreenIdle);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleLocaleChange(Context context) {
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_LOCALE_CHANGED);
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }

    private void handleCardStatusChange(Context context, Intent intent) {
        // If the Card is absent then check if the StkAppService is even
        // running before starting it to stop it right away
        if ((intent.getBooleanExtra(AppInterface.CARD_STATUS, false) == false)
                && StkAppService.getInstance() == null) {
            //CatLog.d(this, "No need to start the StkAppService just to stop it again");
            return;
        }
        Bundle args = new Bundle();
        args.putInt(StkAppService.OPCODE, StkAppService.OP_CARD_STATUS_CHANGED);
        args.putBoolean(AppInterface.CARD_STATUS,
                intent.getBooleanExtra(AppInterface.CARD_STATUS,true));
        args.putInt(AppInterface.REFRESH_RESULT, intent
                .getIntExtra(AppInterface.REFRESH_RESULT,
                IccRefreshResponse.Result.ICC_FILE_UPDATE.ordinal()));
        args.putInt(StkAppService.SLOT_ID, intent
                .getIntExtra("SLOT_ID",0));
        context.startService(new Intent(context, StkAppService.class)
                .putExtras(args));
    }
}
