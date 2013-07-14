/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2009,2012 Code Aurora Forum. All rights reserved.
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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.cat.AppInterface;
import com.android.internal.telephony.cat.LaunchBrowserMode;
import com.android.internal.telephony.cat.Menu;
import com.android.internal.telephony.cat.Item;
import com.android.internal.telephony.cat.Input;
import com.android.internal.telephony.cat.ResultCode;
import com.android.internal.telephony.cat.CatCmdMessage;
import com.android.internal.telephony.cat.ToneSettings;
import com.android.internal.telephony.cat.CatCmdMessage.BrowserSettings;
import com.android.internal.telephony.cat.CatCmdMessage.SetupEventListSettings;
import com.android.internal.telephony.cat.CatLog;
import com.android.internal.telephony.cat.CatResponseMessage;
import com.android.internal.telephony.cat.TextMessage;
import com.android.internal.telephony.GsmAlphabet;
import android.app.ActivityManager;
import com.android.internal.telephony.IccRefreshResponse;
import com.android.internal.telephony.IccCardStatus.CardState;
import com.android.internal.telephony.UiccManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

import static com.android.internal.telephony.cat.CatCmdMessage.SetupEventListConstants.*;

/**
 * SIM toolkit application level service. Interacts with Telephopny messages,
 * application's launch and user input from STK UI elements.
 *
 */
public class StkAppService extends Service {

    // members
    private volatile ServiceHandler[] mServiceHandler;
    private AppInterface[] mStkService;
    private Context mContext = null;
    private NotificationManager mNotificationManager = null;
    private HandlerThread[]  mHandlerThread;
    private static int mCardNum = 0;

    static StkAppService sInstance = null;

    // Used for setting FLAG_ACTIVITY_NO_USER_ACTION when
    // creating an intent.
    private enum InitiatedByUserAction {
        yes,            // The action was started via a user initiated action
        unknown,        // Not known for sure if user initated the action
    }

    // constants
    static final String OPCODE = "op";
    static final String CMD_MSG = "cmd message";
    static final String RES_ID = "response id";
    static final String MENU_SELECTION = "menu selection";
    static final String INPUT = "input";
    static final String HELP = "help";
    static final String CONFIRMATION = "confirm";
    static final String SCREEN_STATUS = "screen status";
    static final String SLOT_ID = "slot_id";

    // These below constants are used for SETUP_EVENT_LIST
    static final String SETUP_EVENT_TYPE = "event";
    static final String SETUP_EVENT_CAUSE = "cause";
    static final String CHOICE = "choice";

    // operations ids for different service functionality.
    static final int OP_CMD = 1;
    static final int OP_RESPONSE = 2;
    static final int OP_LAUNCH_APP = 3;
    static final int OP_END_SESSION = 4;
    static final int OP_BOOT_COMPLETED = 5;
    private static final int OP_DELAYED_MSG = 6;
    static final int OP_ALPHA_NOTIFY = 30;
    static final int OP_IDLE_SCREEN = 7;
    static final int OP_LOCALE_CHANGED = 9;
    static final int OP_CARD_STATUS_CHANGED = 10;
    static final int OP_AIRPLANE_MODE = 25;

    //Invalid SetupEvent
    static final int INVALID_SETUP_EVENT = 0xFF;

    // Response ids
    static final int RES_ID_MENU_SELECTION = 11;
    static final int RES_ID_INPUT = 12;
    static final int RES_ID_CONFIRM = 13;
    static final int RES_ID_DONE = 14;
    static final int RES_ID_CHOICE = 15;

    static final int RES_ID_TIMEOUT = 20;
    static final int RES_ID_BACKWARD = 21;
    static final int RES_ID_END_SESSION = 22;
    static final int RES_ID_EXIT = 23;

    static final int YES = 1;
    static final int NO = 0;

    private static final String PACKAGE_NAME = "com.android.stk";
    private static final String MENU_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkMenuActivity";
    private static final String INPUT_ACTIVITY_NAME =
                                        PACKAGE_NAME + ".StkInputActivity";

    // Notification id used to display Idle Mode text in NotificationManager.
    private static final int STK_NOTIFICATION_ID = 333;
    // Message id to signal tone duration timeout.
    private static final int MSG_ID_STOP_TONE = 0xda;

    // ID to  distinguish the main handler to handle OP_LAUNCH_APP
    private static final int SECONDARY = 0;
    private static final int MAIN = 1;

    @Override
    public void onCreate() {

        // Get number of Cards
        UiccManager mUiccManager;
        mCardNum = android.telephony.TelephonyManager.getDefault().getPhoneCount();
        mStkService = new AppInterface[mCardNum];
        mHandlerThread = new HandlerThread[mCardNum];
        mServiceHandler = new ServiceHandler[mCardNum];
        mUiccManager = UiccManager.getInstance();

        CatLog.d(this, " Number of Cards present:"+mCardNum);
        CatLog.d(this, " GetInstance: "+ mUiccManager);

        boolean catInstancePresent = false;

        for (int i = 0; i < mCardNum; i++) {
            if (mUiccManager != null) {
                mStkService[i] = mUiccManager.getCatService(i);
            }
            if (mStkService[i] == null) {
                CatLog.d(this, " GetCatServiceInstance for "+ i+ "is null: ");
            } else {
                catInstancePresent = true;
                mHandlerThread[i] = new HandlerThread("ServiceHandler"+ i);
                mHandlerThread[i].start();
                // Get the HandlerThread's Looper and use it for our Handler
                mServiceHandler[i] = new ServiceHandler(mHandlerThread[i].getLooper(), i);
            }
        }

        if (!catInstancePresent) {
            CatLog.d(this, " Unable to get Service handle for any Instance");
            stopSelf();
            return;
        }

        mContext = getBaseContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        sInstance = this;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        waitForLooper();

        // onStart() method can be passed a null intent
        // TODO: replace onStart() with onStartCommand()
        if (intent == null) {
            return;
        }

        Bundle args = intent.getExtras();

        if (args == null) {
            return;
        }

        int  slotId = args.getInt(SLOT_ID);
        if (mStkService[slotId] == null) {
            CatLog.d(this, "Returning as CatService on"+ slotId+ "is null");
            //stopSelf();
            return;
        }

        Message msg = mServiceHandler[slotId].obtainMessage();
        msg.arg1 = args.getInt(OPCODE);
        CatLog.d(this,  msg.arg1+ "called on slot:"+ slotId);
        switch(msg.arg1) {
        case OP_CMD:
            msg.obj = args.getParcelable(CMD_MSG);
            break;
        case OP_RESPONSE:
        case OP_CARD_STATUS_CHANGED:
        case OP_ALPHA_NOTIFY:
            msg.obj = args;
        case OP_END_SESSION:
            break;
        case OP_LOCALE_CHANGED:
        case OP_IDLE_SCREEN:
            msg.obj = args;
        case OP_LAUNCH_APP:
            msg.arg2 = MAIN;
        case OP_BOOT_COMPLETED:
        case OP_AIRPLANE_MODE:
            //Broadcast this event to other slots.
            for (int i = 0; i < mCardNum; i++) {
                if ((i != slotId) && (mServiceHandler[i] != null)) {
                    Message tmpmsg = mServiceHandler[i].obtainMessage();
                    tmpmsg.arg1 = msg.arg1;
                    tmpmsg.arg2 = SECONDARY;
                    tmpmsg.obj = msg.obj;
                    mServiceHandler[i].sendMessage(tmpmsg);
                }
            }
            break;
        default:
            return;
        }
        mServiceHandler[slotId].sendMessage(msg);
    }

    @Override
    public void onDestroy() {
        waitForLooper();
        for (int i = 0; i < mCardNum; i++) {
            if (mHandlerThread[i] != null) {
                mHandlerThread[i].quit();
            }
        }
        sInstance = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    /*
     * Package api used by StkDialogActivity to indicate if its on the foreground.
     */
    void indicateDisplayTextDlgVisibility(boolean visibility,int slotId) {
        mServiceHandler[slotId].indicateDisplayTextDlgVisibility(visibility);
    }

    /*
     * Package api used by StkMenuActivity to indicate if its on the foreground.
     */
    void indicateMenuVisibility(boolean visibility,int slotId) {
        mServiceHandler[slotId].indicateMenuVisibility(visibility);
    }

    /*
     * Package api used by StkMenuActivity to get its Menu parameter.
     */
    Menu getMenu(int slotId) {
        CatLog.d(this, "Menu on "+ slotId+ " selected");
        return mServiceHandler[slotId].getMainMenu();
    }

    private String getSimOperatorName(int subscription) {
        if (TelephonyManager.getDefault().isMultiSimEnabled())
            return MSimTelephonyManager.getDefault().getSimOperatorName(subscription);
        return TelephonyManager.getDefault().getSimOperatorName();
    }

    /*
     * Package api used by UI Activities and Dialogs to communicate directly
     * with the service to deliver state information and parameters.
     */
    static StkAppService getInstance() {
        return sInstance;
    }

    private void waitForLooper() {
        while (mServiceHandler == null) {
            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private final class ServiceHandler extends Handler {

    private CatCmdMessage mMainCmd = null;
    private CatCmdMessage mCurrentCmd = null;
    private Menu mCurrentMenu = null;
    private String lastSelectedItem = null;
    private boolean responseNeeded = true;
    private boolean mCmdInProgress = false;
    private boolean launchBrowser = false;
    private BrowserSettings mBrowserSettings = null;
    private SetupEventListSettings mSetupEventListSettings = null;
    private LinkedList<DelayedCmd> mCmdsQ = new LinkedList<DelayedCmd>();
    private CatCmdMessage mCurrentSetupEventCmd = null;
    private CatCmdMessage mIdleModeTextCmd = null;
    private boolean mDisplayText = false;
    private boolean mScreenIdle = true;
    TonePlayer player = null;
    Vibrator mVibrator = new Vibrator();
    private Menu mMainMenu  = null;
    private int mCurrentSlotId = 0;
    private boolean mClearSelectItem = false;
    private boolean mDisplayTextDlgIsVisibile = false;
    private boolean mMenuIsVisibile = false;

    // message id for time out
    private static final int MSG_ID_TIMEOUT = 1;

    Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case MSG_ID_TIMEOUT:
                CatLog.d(this, "SELECT ITEM EXPIRED");
                handleSessionEnd();
                break;
             }
        }
    };

    private void cancelTimeOut() {
        mTimeoutHandler.removeMessages(MSG_ID_TIMEOUT);
    }

    private void startTimeOut() {
        // Reset timeout.
        cancelTimeOut();
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler
                .obtainMessage(MSG_ID_TIMEOUT), StkApp.SELECT_ITEM_TIMEOUT);
    }

    // Inner class used for queuing telephony messages (proactive commands,
    // session end) while the service is busy processing a previous message.
    private class DelayedCmd {
        // members
        int id;
        CatCmdMessage msg;

        DelayedCmd(int id, CatCmdMessage msg) {
            this.id = id;
            this.msg = msg;
        }
    }


    public ServiceHandler(Looper looper, int slotId) {
        super(looper);
        mCmdsQ = new LinkedList<DelayedCmd>();
        mCurrentSlotId = slotId;
    }

    @Override
    public void handleMessage(Message msg) {
        int opcode = msg.arg1;

        switch (opcode) {
            case OP_LAUNCH_APP:
                int isMain = msg.arg2;
                CatLog.d(this, "OP_LAUNCH_APP isMain: " + isMain);
                if (mMainCmd == null) {
                    // nothing todo when no SET UP MENU command didn't arrive.
                    return;
                }
                if (mCurrentCmd != null) {
                    //End the previous command
                    //which may not end correctly when user press the home key
                    //we need to end both slot1 and slot2 's current command.
                    userEndSession();
                }
                if ( isMain == MAIN) {
                    launchMenuActivity(null);
                }
                break;
            case OP_CMD:
                CatCmdMessage cmdMsg = (CatCmdMessage) msg.obj;
                CatLog.d(this, "OP_CMD: " + cmdMsg);

                //Cancel the timer if it is set.
                cancelTimeOut();

                // There are two types of commands:
                // 1. Interactive - user's response is required.
                // 2. Informative - display a message, no interaction with the user.
                //
                // Informative commands can be handled immediately without any delay.
                // Interactive commands can't override each other. So if a command
                // is already in progress, we need to queue the next command until
                // the user has responded or a timeout expired.
                if (!isCmdInteractive(cmdMsg)) {
                    handleCmd(cmdMsg);
                } else {
                    if (!mCmdInProgress) {
                        mCmdInProgress = true;
                        handleCmd((CatCmdMessage) msg.obj);
                    } else {
                        mCmdsQ.addLast(new DelayedCmd(OP_CMD,
                                (CatCmdMessage) msg.obj));
                    }
                }
                break;
            case OP_RESPONSE:
                if (responseNeeded) {
                    handleCmdResponse((Bundle) msg.obj);
                }
                // call delayed commands if needed.
                if (mCmdsQ.size() != 0) {
                    callDelayedMsg();
                } else {
                    mCmdInProgress = false;
                }
                // reset response needed state var to its original value.
                responseNeeded = true;
                break;
            case OP_END_SESSION:
                if (!mCmdInProgress) {
                    mCmdInProgress = true;
                    handleSessionEnd();
                } else {
                    mCmdsQ.addLast(new DelayedCmd(OP_END_SESSION, null));
                }
                break;
            case OP_BOOT_COMPLETED:
                CatLog.d(this, "OP_BOOT_COMPLETED");
                if (mMainCmd == null) {
                    StkAppInstaller.unInstall(mContext, mCurrentSlotId);
                }
                break;
            case OP_AIRPLANE_MODE:
                CatLog.d(this, "OP_AIRPLANE_MODE");
                if (isAirplaneModeOn()) {
                    StkAppInstaller.unInstall(mContext, mCurrentSlotId);
                } else if (mMainCmd != null) {
                    StkAppInstaller.install(mContext, mCurrentSlotId);
                }
                break;
            case OP_DELAYED_MSG:
                handleDelayedCmd();
                break;
            case OP_ALPHA_NOTIFY:
                handleAlphaNotify((Bundle) msg.obj);
            case OP_IDLE_SCREEN:
                CatLog.d(this, "IDLE SCREEN");
                handleScreenStatus((Bundle) msg.obj);
                break;
            case OP_LOCALE_CHANGED:
                CatLog.d(this, "Locale Changed");
                checkForSetupEvent(LANGUAGE_SELECTION_EVENT,(Bundle) msg.obj);
                break;
            case MSG_ID_STOP_TONE:
                CatLog.d(this, "Received MSG_ID_STOP_TONE");
                handleStopTone();
                break;
            case OP_CARD_STATUS_CHANGED:
                CatLog.d(this, "Card/Icc Status change received");
                handleCardStatusChangeAndIccRefresh((Bundle) msg.obj);
                break;
        }
    }


    private void handleCardStatusChangeAndIccRefresh(Bundle args) {
        boolean CardStatus = args.getBoolean(AppInterface.CARD_STATUS);

        CatLog.d(this, "CardStatus: " + CardStatus);
        if (CardStatus == false) {
            CatLog.d(this, "CARD is ABSENT");
            // Uninstall STKAPP, Clear Idle text, Stop StkAppService
            StkAppInstaller.unInstall(mContext, mCurrentSlotId);
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
            stopSelf();
        } else {
            IccRefreshResponse state = new IccRefreshResponse();
            state.refreshResult = IccRefreshResponse.refreshResultFromRIL(
                    args.getInt(AppInterface.REFRESH_RESULT));

            CatLog.d(this, "Icc Refresh Result: "+ state.refreshResult);
            if ((state.refreshResult == IccRefreshResponse.Result.ICC_INIT) ||
                (state.refreshResult == IccRefreshResponse.Result.ICC_RESET)) {
                // Clear Idle Text
                mNotificationManager.cancel(STK_NOTIFICATION_ID);
                mIdleModeTextCmd = null;
            }

            if (state.refreshResult == IccRefreshResponse.Result.ICC_RESET) {
                // Uninstall STkmenu
                StkAppInstaller.unInstall(mContext, mCurrentSlotId);
                mCurrentMenu = null;
                mMainCmd = null;
            }
        }
    }

    private void handleScreenStatus(Bundle args) {
        mScreenIdle = args.getBoolean(SCREEN_STATUS);

        // If the idle screen event is present in the list need to send the
        // response to SIM.
        if (mScreenIdle) {
            CatLog.d(this, "Need to send IDLE SCREEN Available event to SIM");
            checkForSetupEvent(IDLE_SCREEN_AVAILABLE_EVENT, null);
        }
        if (mIdleModeTextCmd != null) {
           launchIdleText();
        }
        if (mDisplayText) {

            /*
             * Check if the screen is NOT idle and also to check if the top most
             * activity visible is not 'us'. We are never busy to ourselves.
             */
            if (!mScreenIdle && !isTopOfStack()) {
                sendScreenBusyResponse();
            } else {
                launchTextDialog();
            }
            mDisplayText = false;
            // If an idle text proactive command is set then the
            // request for getting screen status still holds true.
            if (mIdleModeTextCmd == null) {
                enableScreenStatusReq(false);
            }
        }
    }

    private boolean isTopOfStack() {
        ActivityManager mAcivityManager = (ActivityManager) mContext
                .getSystemService(ACTIVITY_SERVICE);
        String currentPackageName = mAcivityManager.getRunningTasks(1).get(0).topActivity
                .getPackageName();
        if (null != currentPackageName) {
            return currentPackageName.equals(PACKAGE_NAME);
        }

        return false;
    }

    private void sendScreenBusyResponse() {
        if (mCurrentCmd == null) {
            return;
        }
        CatResponseMessage resMsg = new CatResponseMessage(mCurrentCmd);
        CatLog.d(this, "SCREEN_BUSY");
        resMsg.setResultCode(ResultCode.TERMINAL_CRNTLY_UNABLE_TO_PROCESS);
        mStkService[mCurrentSlotId].onCmdResponse(resMsg);
        // reset response needed state var to its original value.
        responseNeeded = true;
        if (mCmdsQ.size() != 0) {
            callDelayedMsg();
        } else {
            mCmdInProgress = false;
        }
    }

    private void handleStopTone() {
        sendResponse(StkAppService.RES_ID_DONE);
        player.stop();
        player.release();
        mVibrator.cancel();
        CatLog.d(this, "Finished handling PlayTone with Null alpha");
    }

    private void sendResponse(int resId) {
        Message msg = this.obtainMessage();
        msg.arg1 = OP_RESPONSE;
        Bundle args = new Bundle();
        args.putInt(StkAppService.RES_ID, resId);
        args.putInt(StkAppService.SLOT_ID, mCurrentSlotId);
        CatLog.d(this, "sendResponse mCurrentSlotId: " + mCurrentSlotId );
        msg.obj = args;
        this.sendMessage(msg);
    }

    private boolean isCmdInteractive(CatCmdMessage cmd) {
        switch (cmd.getCmdType()) {
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
        case SET_UP_IDLE_MODE_TEXT:
        case SET_UP_MENU:
        case SET_UP_EVENT_LIST:
        case CLOSE_CHANNEL:
        case RECEIVE_DATA:
        case SEND_DATA:
            return false;
        }

        return true;
    }

    private void handleDelayedCmd() {
        if (mCmdsQ.size() != 0) {
            DelayedCmd cmd = mCmdsQ.poll();
            switch (cmd.id) {
            case OP_CMD:
                handleCmd(cmd.msg);
                break;
            case OP_END_SESSION:
                handleSessionEnd();
                break;
            }
        }
    }

    private void callDelayedMsg() {
        Message msg = this.obtainMessage();
        msg.arg1 = OP_DELAYED_MSG;
        this.sendMessage(msg);
    }

    private void handleSessionEnd() {
        mCurrentCmd = mMainCmd;
        lastSelectedItem = null;
        cancelTimeOut();

        // In case of SET UP MENU command which removed the app, don't
        // update the current menu member.
        if (mCurrentMenu != null && mMainCmd != null) {
            mCurrentMenu = mMainCmd.getMenu();
        }
        if (mMenuIsVisibile) {
            launchMenuActivity(null);
        }
        if (mCmdsQ.size() != 0) {
            callDelayedMsg();
        } else {
            mCmdInProgress = false;
        }
        // In case a launch browser command was just confirmed, launch that url.
        if (launchBrowser) {
            launchBrowser = false;
            launchBrowser(mBrowserSettings);
        }
    }

    private void handleCmd(CatCmdMessage cmdMsg) {
        if (cmdMsg == null) {
            return;
        }
        // save local reference for state tracking.
        mCurrentCmd = cmdMsg;
        boolean waitForUsersResponse = true;

        CatLog.d(this, cmdMsg.getCmdType().name());
        switch (cmdMsg.getCmdType()) {
        case DISPLAY_TEXT:
            TextMessage msg = cmdMsg.geTextMessage();
            //In case when TR already sent no response is expected by Stk app
            if (!msg.responseNeeded) {
                waitForUsersResponse = false;
            }
            if (lastSelectedItem != null) {
                msg.title = lastSelectedItem;
            } else if (mMainCmd != null){
                msg.title = mMainCmd.getMenu().title;
            } else {
                // get the carrier name from the SIM
                msg.title = getSimOperatorName(mCurrentSlotId);
            }

            //If the device is not in idlescreen and a low priority display
            //text message command arrives then send screen busy terminal
            //response with out displaying the message. Otherwise display the
            //message. The existing displayed message shall be updated with the
            //new display text proactive command (Refer to ETSI TS 102 384
            //section 27.22.4.1.4.4.2).
            if (!(msg.isHighPriority || mMenuIsVisibile || mDisplayTextDlgIsVisibile)) {
                enableScreenStatusReq(true);
                mDisplayText = true;
            } else {
                launchTextDialog();
            }
            break;
        case SELECT_ITEM:
            mCurrentMenu = cmdMsg.getMenu();
            launchMenuActivity(cmdMsg.getMenu());
            break;
        case SET_UP_MENU:
            mMainCmd = mCurrentCmd;
            mCurrentMenu = cmdMsg.getMenu();
            if (removeMenu()) {
                CatLog.d(this, "Uninstall App");
                StkAppInstaller.unInstall(mContext, mCurrentSlotId);
            } else {
                CatLog.d(this, "Install App");
                StkAppInstaller.install(mContext, mCurrentSlotId);
            }
            mMainMenu = mCurrentMenu;
            if (mMenuIsVisibile) {
                launchMenuActivity(null);
            }
            break;
        case GET_INPUT:
        case GET_INKEY:
            launchInputActivity();
            break;
        case SET_UP_IDLE_MODE_TEXT:
            waitForUsersResponse = false;
            mIdleModeTextCmd = mCurrentCmd;
            launchIdleText();
            TextMessage idleModeText = mCurrentCmd.geTextMessage();
            // Send intent to ActivityManagerService to get the screen status
            Intent idleStkIntent  = new Intent(AppInterface.CHECK_SCREEN_IDLE_ACTION);
            if (idleModeText != null) {
                if (idleModeText.text != null) {
                    idleStkIntent.putExtra("SCREEN_STATUS_REQUEST",true);
                } else {
                    idleStkIntent.putExtra("SCREEN_STATUS_REQUEST",false);
                    launchIdleText();
                    mIdleModeTextCmd = null;
                }
            }
            CatLog.d(this, "set up idle mode");
            mCurrentCmd = mMainCmd;
            sendBroadcast(idleStkIntent);
            break;
        case SEND_DTMF:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
            waitForUsersResponse = false;
            launchEventMessage();
            mCurrentCmd = mMainCmd;
            break;
        case LAUNCH_BROWSER:
            launchConfirmationDialog(mCurrentCmd.geTextMessage());
            break;
        case GET_CHANNEL_STATUS:
            waitForUsersResponse = false;
            launchEventMessage();
            break;
        case SET_UP_CALL:
            TextMessage mesg = mCurrentCmd.getCallSettings().confirmMsg;
            if ((mesg != null) && (mesg.text == null || mesg.text.length() == 0)) {
                mesg.text = getResources().getString(R.string.default_setup_call_msg);
            }
            CatLog.d(this, "SET_UP_CALL mesg.text " + mesg.text);
            launchConfirmationDialog(mesg);
            break;
        case PLAY_TONE:
            launchToneDialog();
            break;
        case SET_UP_EVENT_LIST:
            CatLog.d(this," SETUP_EVENT_LIST");

            mSetupEventListSettings = mCurrentCmd.getSetEventList();
            mCurrentSetupEventCmd = mCurrentCmd;
            mCurrentCmd = mMainCmd;
            if ((mIdleModeTextCmd == null) && (!mDisplayText)) {

                for (int i = 0; i < mSetupEventListSettings.eventList.length; i++) {
                    if (mSetupEventListSettings.eventList[i] == IDLE_SCREEN_AVAILABLE_EVENT) {
                        CatLog.d(this," IDLE_SCREEN_AVAILABLE_EVENT present in List");
                        // Request ActivityManagerService to get the screen status
                        enableScreenStatusReq(true);
                        break;
                    }
                }
            }
            break;
	case OPEN_CHANNEL:
            launchOpenChannelDialog();
            break;
        case CLOSE_CHANNEL:
        case RECEIVE_DATA:
        case SEND_DATA:
            TextMessage m = mCurrentCmd.geTextMessage();

            if ((m != null) && (m.text == null)) {
                switch(cmdMsg.getCmdType()) {
                case CLOSE_CHANNEL:
                    m.text = getResources().getString(R.string.default_close_channel_msg);
                    break;
                case RECEIVE_DATA:
                    m.text = getResources().getString(R.string.default_receive_data_msg);
                    break;
                case SEND_DATA:
                    m.text = getResources().getString(R.string.default_send_data_msg);
                    break;
                }
            }
            launchTransientEventMessage();
            break;
        }

        if (!waitForUsersResponse) {
            if (mCmdsQ.size() != 0) {
                callDelayedMsg();
            } else {
                mCmdInProgress = false;
            }
        }
    }

    private void userEndSession() {
        CatResponseMessage resMsg = new CatResponseMessage(mCurrentCmd);
        CatLog.d(this, "userEndSession command is  " + mCurrentCmd.getCmdType().name());
        switch (mCurrentCmd.getCmdType()) {
        case SET_UP_MENU:
            break;
        default:
            resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
            if (mStkService[mCurrentSlotId] != null) {
                mStkService[mCurrentSlotId].onCmdResponse(resMsg);
            } else {
                CatLog.d(this, "CmdResponse on wrong slotid");
            }
            break;
        }
    }


    /*
     * Package api used by StkMenuActivity to indicate if its on the foreground.
     */
    void indicateMenuVisibility(boolean visibility) {
        mMenuIsVisibile = visibility;
    }

    /*
     * Package api used by StkMenuActivity to get its Menu parameter.
     */
    public Menu getMainMenu() {
        return mMainMenu;
    }

    private void handleCmdResponse(Bundle args) {
        if (mCurrentCmd == null) {
            return;
        }
        CatResponseMessage resMsg = new CatResponseMessage(mCurrentCmd);

        // set result code
        boolean helpRequired = args.getBoolean(HELP, false);

        switch(args.getInt(RES_ID)) {
        case RES_ID_MENU_SELECTION:
            CatLog.d(this, "RES_ID_MENU_SELECTION");
            int menuSelection = args.getInt(MENU_SELECTION);
            switch(mCurrentCmd.getCmdType()) {
            case SET_UP_MENU:
            case SELECT_ITEM:
                lastSelectedItem = getItemName(menuSelection);
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode(mCurrentCmd.hasIconLoadFailed() ?
                            ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK);
                }
                resMsg.setMenuSelection(menuSelection);
                startTimeOut();
                break;
            }
            break;
        case RES_ID_INPUT:
            CatLog.d(this, "RES_ID_INPUT");
            String input = args.getString(INPUT);
            Input cmdInput = mCurrentCmd.geInput();
            if (cmdInput != null && cmdInput.yesNo) {
                boolean yesNoSelection = input
                        .equals(StkInputActivity.YES_STR_RESPONSE);
                resMsg.setYesNo(yesNoSelection);
            } else {
                if (helpRequired) {
                    resMsg.setResultCode(ResultCode.HELP_INFO_REQUIRED);
                } else {
                    resMsg.setResultCode(mCurrentCmd.hasIconLoadFailed() ?
                            ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK);
                    resMsg.setInput(input);
                }
            }
            break;
        case RES_ID_CONFIRM:
            CatLog.d(this, "RES_ID_CONFIRM");
            boolean confirmed = args.getBoolean(CONFIRMATION);
            switch (mCurrentCmd.getCmdType()) {
            case DISPLAY_TEXT:
                if (confirmed) {
                    resMsg.setResultCode(mCurrentCmd.hasIconLoadFailed() ?
                            ResultCode.PRFRMD_ICON_NOT_DISPLAYED : ResultCode.OK);
                } else {
                    resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
                }
                break;
            case LAUNCH_BROWSER:
                mBrowserSettings = mCurrentCmd.getBrowserSettings();
                /* If Launch Browser mode is LAUNCH_IF_NOT_ALREADY_LAUNCHED and if the browser is already launched
                 * then send the error code and additional info indicating 'Browser Unavilable'(0x02)
                 */
                if( (mBrowserSettings.mode ==  LaunchBrowserMode.LAUNCH_IF_NOT_ALREADY_LAUNCHED) &&
                                               confirmed && isBrowserLaunched(mContext)) {
                    resMsg.setResultCode(ResultCode.LAUNCH_BROWSER_ERROR);
                    resMsg.setAdditionalInfo(true,0x02);
                    CatLog.d(this, "LAUNCH_BROWSER_ERROR - Browser_Unavailable");
                } else {
                    resMsg.setResultCode(confirmed ? ResultCode.OK
                                                   : ResultCode.UICC_SESSION_TERM_BY_USER);
                }

                if (confirmed) {
                    launchBrowser = true;
                }
                break;
            case OPEN_CHANNEL:
            case SET_UP_CALL:
                resMsg.setResultCode(ResultCode.OK);
                resMsg.setConfirmation(confirmed);
                if (confirmed) {
                    launchCallMsg();
                }
                break;
            }
            break;
        case RES_ID_DONE:
            resMsg.setResultCode(ResultCode.OK);
            break;
        case RES_ID_BACKWARD:
            CatLog.d(this, "RES_ID_BACKWARD");
            resMsg.setResultCode(ResultCode.BACKWARD_MOVE_BY_USER);
            break;
        case RES_ID_END_SESSION:
            CatLog.d(this, "RES_ID_END_SESSION");
            resMsg.setResultCode(ResultCode.UICC_SESSION_TERM_BY_USER);
            break;
        case RES_ID_TIMEOUT:
            CatLog.d(this, "RES_ID_TIMEOUT");
            // GCF test-case 27.22.4.1.1 Expected Sequence 1.5 (DISPLAY TEXT,
            // Clear message after delay, successful) expects result code OK.
            // If the command qualifier specifies no user response is required
            // then send OK instead of NO_RESPONSE_FROM_USER
            if ((mCurrentCmd.getCmdType().value() == AppInterface.CommandType.DISPLAY_TEXT
                    .value())
                    && (mCurrentCmd.geTextMessage().userClear == false)) {
                resMsg.setResultCode(ResultCode.OK);
            } else {
                resMsg.setResultCode(ResultCode.NO_RESPONSE_FROM_USER);
            }
            break;
        case RES_ID_CHOICE:
            int choice = args.getInt(CHOICE);
            CatLog.d(this, "User Choice=" + choice);
            switch (choice) {
                case YES:
                    resMsg.setResultCode(ResultCode.OK);
                    break;
                case NO:
                    resMsg.setResultCode(ResultCode.USER_NOT_ACCEPT);
                    break;
            }
            break;
        default:
            CatLog.d(this, "Unknown result id");
            return;
        }

        int slotId = args.getInt(SLOT_ID);
        if (mStkService[slotId] != null) {

            CatLog.d(this, "CmdResponse sent on"+ slotId);
            mStkService[slotId].onCmdResponse(resMsg);

        } else {
            CatLog.d(this, "CmdResponse on wrong slotid");
        }
    }

    /*
     * Package api used by StkDialogActivity to indicate if its on the foreground.
     */
    void indicateDisplayTextDlgVisibility(boolean visibility) {
        mDisplayTextDlgIsVisibile = visibility;
    }

    /**
     * Returns 0 or FLAG_ACTIVITY_NO_USER_ACTION, 0 means the user initiated the action.
     *
     * @param userAction If the userAction is yes then we always return 0 otherwise
     * mMenuIsVisible is used to determine what to return. If mMenuIsVisible is true
     * then we are the foreground app and we'll return 0 as from our perspective a
     * user action did cause. If it's false than we aren't the foreground app and
     * FLAG_ACTIVITY_NO_USER_ACTION is returned.
     *
     * @return 0 or FLAG_ACTIVITY_NO_USER_ACTION
     */
    private int getFlagActivityNoUserAction(InitiatedByUserAction userAction) {
        return ((userAction == InitiatedByUserAction.yes) | mMenuIsVisibile) ?
                                                    0 : Intent.FLAG_ACTIVITY_NO_USER_ACTION;
    }

    private void launchMenuActivity(Menu menu) {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setClassName(PACKAGE_NAME, MENU_ACTIVITY_NAME);
        int intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        if (menu == null) {
            // We assume this was initiated by the user pressing the tool kit icon
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.yes);
            newIntent.putExtra("STATE", StkMenuActivity.STATE_MAIN);
        } else {
            // We don't know and we'll let getFlagActivityNoUserAction decide.
            intentFlags |= getFlagActivityNoUserAction(InitiatedByUserAction.unknown);
            newIntent.putExtra("STATE", StkMenuActivity.STATE_SECONDARY);
            newIntent.putExtra("MENU", menu);
        }
        newIntent.putExtra(SLOT_ID, mCurrentSlotId);
        newIntent.setFlags(intentFlags);
        mContext.startActivity(newIntent);
    }

    private void launchInputActivity() {
        Intent newIntent = new Intent(Intent.ACTION_VIEW);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.setClassName(PACKAGE_NAME, INPUT_ACTIVITY_NAME);
        newIntent.putExtra("INPUT", mCurrentCmd.geInput());
        newIntent.putExtra(SLOT_ID, mCurrentSlotId);
        mContext.startActivity(newIntent);
    }

    private void launchTextDialog() {
        Intent newIntent = new Intent(sInstance, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", mCurrentCmd.geTextMessage());
        newIntent.putExtra(SLOT_ID, mCurrentSlotId);
        startActivity(newIntent);
    }

    private void sendSetUpEventResponse(int event, byte[] addedInfo) {
        CatLog.d(this, "sendSetUpEventResponse: event : " + event);

        if (mCurrentSetupEventCmd == null){
            return;
        }

        CatResponseMessage resMsg = new CatResponseMessage(mCurrentSetupEventCmd);

        resMsg.setResultCode(ResultCode.OK);
        resMsg.setEventDownload(event, addedInfo);

        mStkService[mCurrentSlotId].onCmdResponse(resMsg);
    }

    private void checkForSetupEvent(int event, Bundle args) {
        boolean eventPresent = false;
        byte[] addedInfo = null;
        CatLog.d(this, "Event :" + event);

        if (mSetupEventListSettings != null) {
            /* Checks if the event is present in the EventList updated by last
             * SetupEventList Proactive Command */
            for (int i = 0; i < mSetupEventListSettings.eventList.length; i++) {
                 if (event == mSetupEventListSettings.eventList[i]) {
                     eventPresent =  true;
                     break;
                 }
            }


            /* If Event is present send the response to ICC */
            if (eventPresent == true) {
                CatLog.d(this, " Event " + event + "exists in the EventList");

                switch (event) {
                    case IDLE_SCREEN_AVAILABLE_EVENT:
                        sendSetUpEventResponse(event, addedInfo);
                        removeSetUpEvent(event);
                        enableScreenStatusReq(false);
                        break;
                    case LANGUAGE_SELECTION_EVENT:
                        String language =  mContext.getResources().getConfiguration().locale.getLanguage();
                        CatLog.d(this, "language: " + language);
                        // Each language code is a pair of alpha-numeric characters. Each alpha-numeric
                        // character shall be coded on one byte using the SMS default 7-bit coded alphabet
                        addedInfo = GsmAlphabet.stringToGsm8BitPacked(language);
                        sendSetUpEventResponse(event, addedInfo);
                        break;
                    default:
                        break;
                }
            } else {
                CatLog.d(this, " Event does not exist in the EventList");
            }
        } else {
            CatLog.d(this, "SetupEventList is not received. Ignoring the event: " + event);
        }
    }

    private void  removeSetUpEvent(int event) {
        CatLog.d(this, "Remove Event :" + event);

        if (mSetupEventListSettings != null) {
            /*
             * Make new  Eventlist without the event
             */
            for (int i = 0; i < mSetupEventListSettings.eventList.length; i++) {
                if (event == mSetupEventListSettings.eventList[i]) {
                    mSetupEventListSettings.eventList[i] = INVALID_SETUP_EVENT;
                    break;
                }
            }
        }
    }

    private void launchEventMessage() {
        TextMessage msg = mCurrentCmd.geTextMessage();
        if (msg == null || msg.text == null) {
            return;
        }
        Toast toast = new Toast(mContext.getApplicationContext());
        LayoutInflater inflate = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.stk_event_msg, null);
        TextView tv = (TextView) v
                .findViewById(com.android.internal.R.id.message);
        ImageView iv = (ImageView) v
                .findViewById(com.android.internal.R.id.icon);
        if (msg.icon != null) {
            iv.setImageBitmap(msg.icon);
        } else {
            iv.setVisibility(View.GONE);
        }
        /** In case of 'self explanatory' stkapp should display the specified
         ** icon in proactive command (but not the alpha string).
         ** If icon is non-self explanatory and if the icon could not be displayed
         ** then alpha string or text data should be displayed
         ** Ref: ETSI 102.223,section 6.5.4
         **/
        if (mCurrentCmd.hasIconLoadFailed() || !msg.iconSelfExplanatory) {
            tv.setText(msg.text);
        }

        toast.setView(v);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }

    private void launchConfirmationDialog(TextMessage msg) {
        msg.title = lastSelectedItem;
        Intent newIntent = new Intent(sInstance, StkDialogActivity.class);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
        newIntent.putExtra("TEXT", msg);
        newIntent.putExtra(SLOT_ID, mCurrentSlotId);
        startActivity(newIntent);
    }

    private void launchBrowser(BrowserSettings settings) {
        if (settings == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);

        Uri data;
        if (settings.url != null) {
            CatLog.d(this, "settings.url = " + settings.url);
            if ((settings.url.startsWith("http://") || (settings.url.startsWith("https://")))) {
                data = Uri.parse(settings.url);
            } else {
                String modifiedUrl = "http://" + settings.url;
                CatLog.d(this, "modifiedUrl = " + modifiedUrl);
                data = Uri.parse(modifiedUrl);
            }
        } else {
            // If no URL specified, just bring up the "home page".
            //
            // (Note we need to specify *something* in the intent's data field
            // here, since if you fire off a VIEW intent with no data at all
            // you'll get an activity chooser rather than the browser.  There's
            // no specific URI that means "use the default home page", so
            // instead let's just explicitly bring up http://google.com.)
            data = Uri.parse("http://google.com/");
        }
        if (data != null) {
	    intent.setData(data);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        switch (settings.mode) {
        case USE_EXISTING_BROWSER:
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        case LAUNCH_NEW_BROWSER:
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            break;
        case LAUNCH_IF_NOT_ALREADY_LAUNCHED:
            if (data != null) {
                intent.setAction(Intent.ACTION_VIEW);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            break;
        }
        // start browser activity
        startActivity(intent);
        // a small delay, let the browser start, before processing the next command.
        // this is good for scenarios where a related DISPLAY TEXT command is
        // followed immediately.
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {}
    }

    private boolean isBrowserLaunched(Context context) {
        int MAX_TASKS = 99;
        ActivityManager mAcivityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if(mAcivityManager == null)
            return false;
        List<RunningTaskInfo> mRunningTasksList = mAcivityManager.getRunningTasks(MAX_TASKS);
        Iterator<RunningTaskInfo> mIterator = mRunningTasksList.iterator();
        while (mIterator.hasNext()) {
              RunningTaskInfo mRunningTask = mIterator.next();
              if (mRunningTask != null) {
                  ComponentName runningTaskComponent = mRunningTask.baseActivity;
                  if (runningTaskComponent.getClassName().equals("com.android.browser.BrowserActivity")) {
                      return true;
                  }
             }
        }
        return false;
    }

    private void launchCallMsg() {
        TextMessage msg = mCurrentCmd.getCallSettings().callMsg;
        if (msg.text == null || msg.text.length() == 0) {
            return;
        }
        msg.title = lastSelectedItem;

        Toast toast = new Toast(mContext.getApplicationContext());
        LayoutInflater inflate = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflate.inflate(R.layout.stk_event_msg, null);
        TextView tv = (TextView) v
                .findViewById(com.android.internal.R.id.message);
        ImageView iv = (ImageView) v
                .findViewById(com.android.internal.R.id.icon);
        if (msg.icon != null) {
            iv.setImageBitmap(msg.icon);
        } else {
            iv.setVisibility(View.GONE);
        }
        /**
         * In case of 'self explanatory' stkapp should display the specified
         * icon in proactive command (but not the alpha string). If icon is
         * non-self explanatory and if the icon could not be displayed then
         * alpha string or text data should be displayed
         **/
        if (msg.icon == null || !msg.iconSelfExplanatory) {
            tv.setText(msg.text);
        }

        toast.setView(v);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();

    }

    private void launchIdleText() {
        TextMessage msg = mIdleModeTextCmd.geTextMessage();
        if (msg == null) {
            CatLog.d(this, "mCurrent.getTextMessage is NULL");
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
            return;
        } else if (msg.text == null || mScreenIdle == false) {
            mNotificationManager.cancel(STK_NOTIFICATION_ID);
        } else {
            Notification notification = new Notification();
            RemoteViews contentView = new RemoteViews(
                    PACKAGE_NAME,
                    com.android.internal.R.layout.status_bar_latest_event_content);

            notification.flags |= Notification.FLAG_NO_CLEAR;
            notification.icon = com.android.internal.R.drawable.stat_notify_sim_toolkit;
            /** In case of 'self explanatory' stkapp should display the specified
             ** icon in proactive command (but not the alpha string).
             ** If icon is non-self explanatory and if the icon could not be displayed
             ** then alpha string or text data should be displayed
             ** Ref: ETSI 102.223,section 6.5.4
             **/
            if (mIdleModeTextCmd.hasIconLoadFailed() || !msg.iconSelfExplanatory) {
                notification.tickerText = msg.text;
                contentView.setTextViewText(com.android.internal.R.id.text,
                        msg.text);
            }
            if (msg.icon != null) {
                contentView.setImageViewBitmap(com.android.internal.R.id.icon,
                        msg.icon);
            } else {
                contentView
                        .setImageViewResource(
                                com.android.internal.R.id.icon,
                                com.android.internal.R.drawable.stat_notify_sim_toolkit);
            }
            notification.contentView = contentView;
            notification.contentIntent = PendingIntent.getService(mContext, 0,
                    new Intent(mContext, StkAppService.class), 0);

            mNotificationManager.notify(STK_NOTIFICATION_ID, notification);
        }
    }

    private void launchToneDialog() {
        TextMessage toneMsg = mCurrentCmd.geTextMessage();
        ToneSettings settings = mCurrentCmd.getToneSettings();
        // Start activity only when there is alpha data otherwise play tone
        if (toneMsg.text != null) {
            CatLog.d(this, "toneMsg.text: " + toneMsg.text + " Starting Activity");
            Intent newIntent = new Intent(sInstance, ToneDialog.class);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | getFlagActivityNoUserAction(InitiatedByUserAction.unknown));
            newIntent.putExtra("TEXT", toneMsg);
            newIntent.putExtra("TONE", settings);
            newIntent.putExtra(SLOT_ID, mCurrentSlotId);
            CatLog.d(this, "Starting Activity based on the ToneDialog Intent for SlotId:"
                    + mCurrentSlotId);
            startActivity(newIntent);
        } else {
            CatLog.d(this, "toneMsg.text: " + toneMsg.text + " NO Activity, play tone");
            onPlayToneNullAlphaTag(toneMsg, settings);
        }
    }

    private void onPlayToneNullAlphaTag(TextMessage toneMsg, ToneSettings settings) {
        // Start playing tone and vibration
        player = new TonePlayer();
        CatLog.d(this, "Play tone settings.tone:" + settings.tone);
        player.play(settings.tone);
        int timeout = StkApp.calculateDurationInMilis(settings.duration);
        CatLog.d(this, "ToneDialog: Tone timeout :" + timeout);
        if (timeout == 0) {
            timeout = StkApp.TONE_DFEAULT_TIMEOUT;
        }
        Message msg = this.obtainMessage();
        msg.arg1 = MSG_ID_STOP_TONE;
        // trigger tone stop after timeout duration
        this.sendMessageDelayed(msg, timeout);
        if (settings.vibrate) {
            mVibrator.vibrate(timeout);
        }
    }

    private void launchOpenChannelDialog() {
        TextMessage msg = mCurrentCmd.geTextMessage();
        if (msg == null) {
            CatLog.d(this, "msg is null, return here");
            return;
        }

        msg.title = getResources().getString(R.string.stk_dialog_title);
        if (msg.text == null) {
            msg.text = getResources().getString(R.string.default_open_channel_msg);
        }

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(msg.title)
                    .setMessage(msg.text)
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(R.string.stk_dialog_accept),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = new Bundle();
                            args.putInt(RES_ID, RES_ID_CHOICE);
                            args.putInt(CHOICE, YES);
                            Message message = obtainMessage();
                            message.arg1 = OP_RESPONSE;
                            message.obj = args;
                            sendMessage(message);
                        }
                    })
                    .setNegativeButton(getResources().getString(R.string.stk_dialog_reject),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = new Bundle();
                            args.putInt(RES_ID, RES_ID_CHOICE);
                            args.putInt(CHOICE, NO);
                            Message message = obtainMessage();
                            message.arg1 = OP_RESPONSE;
                            message.obj = args;
                            sendMessage(message);
                        }
                    })
                    .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.show();
    }

    private void launchTransientEventMessage() {
        TextMessage msg = mCurrentCmd.geTextMessage();
        if (msg == null) {
            CatLog.d(this, "msg is null, return here");
            return;
        }

        msg.title = getResources().getString(R.string.stk_dialog_title);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(msg.title)
                    .setMessage(msg.text)
                    .setCancelable(false)
                    .setPositiveButton(getResources().getString(android.R.string.ok),
                                       new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        if (!mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }

        dialog.show();
    }

    private String getItemName(int itemId) {
        Menu menu = mCurrentCmd.getMenu();
        if (menu == null) {
            return null;
        }
        for (Item item : menu.items) {
            if (item.id == itemId) {
                return item.text;
            }
        }
        return null;
    }

    private boolean removeMenu() {
        try {
            if (mCurrentMenu.items.size() == 1 &&
                mCurrentMenu.items.get(0) == null) {
                return true;
            }
        } catch (NullPointerException e) {
            CatLog.d(this, "Unable to get Menu's items size");
            return true;
        }
        return false;
    }

    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void handleAlphaNotify(Bundle args) {
        String alphaString = args.getString(AppInterface.ALPHA_STRING);

        CatLog.d(this, "Alpha string received from card: " + alphaString);
        Toast toast = Toast.makeText(mContext, alphaString, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
    }

    private void enableScreenStatusReq(boolean enable) {
        CatLog.d(this, "enableScreenStatusReq: " + enable);

        Intent StkIntent = new Intent(AppInterface.CHECK_SCREEN_IDLE_ACTION);
        StkIntent.putExtra("SCREEN_STATUS_REQUEST", enable);
        sendBroadcast(StkIntent);
    }
  } // End of Service Handler class
}
