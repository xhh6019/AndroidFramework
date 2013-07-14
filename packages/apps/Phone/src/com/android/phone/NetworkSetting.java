/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.widget.Toast;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.MSimPhoneFactory;
import com.android.internal.telephony.MSimProxyManager;
import com.android.internal.telephony.OperatorInfo;

import java.util.HashMap;
import java.util.List;

import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity
        implements DialogInterface.OnCancelListener,
                   DialogInterface.OnClickListener{

    private static final String LOG_TAG = "phone";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;
    private static final int EVENT_AUTO_SELECT_DONE = 300;
    private static final int EVENT_SCAN_TIME_OUT = 400;
    private static final int EVENT_NETWOKK_DISCONNECT_DATA_DONE = 500;
    private static final int MAX_SCAN_TIME_OUT = 5*60*1000; // 5 minutes

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;
    private static final int DIALOG_NETWORK_AUTO_SELECT = 300;
    private static final int DIALOG_NETWORK_DISCONNECT_DATA_CONFIRM = 400;

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final String BUTTON_SRCH_NETWRKS_KEY = "button_srch_netwrks_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";

    //map of network controls to the network data.
    private HashMap<Preference, OperatorInfo> mNetworkMap;

    Phone mPhone;
    protected boolean mIsForeground = false;

    /** message for network selection */
    String mNetworkSelectMsg;
    private boolean mNetworkSearchDataDisabled = false;

    //preference objects
    private PreferenceGroup mNetworkList;
    private Preference mSearchButton;
    private Preference mAutoSelect;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded ((List<OperatorInfo>) msg.obj, msg.arg1);
                    break;
                case EVENT_SCAN_TIME_OUT:
                    log("network scan timeout!");
                    try {
                        mNetworkQueryService.stopNetworkQuery(mCallback);
                        dismissDialog(DIALOG_NETWORK_LIST_LOAD);
                    } catch (RemoteException e) {
                        log("RemoteException[stopQuery]: " + e);
                    } catch (IllegalArgumentException e1) {
                        log("RemoteException[stopQuery]: " + e1);
                    }
                    MSimProxyManager.getInstance().enableDataConnectivity(MSimPhoneFactory.getDataSubscription());
                    mNetworkSearchDataDisabled = false;
                    getPreferenceScreen().setEnabled(true);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) log("hideProgressPanel");
                    removeDialog(DIALOG_NETWORK_SELECTION);
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("manual network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("manual network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break;
                case EVENT_AUTO_SELECT_DONE:
                    if (DBG) log("hideProgressPanel");

                    if (mIsForeground) {
                        dismissDialog(DIALOG_NETWORK_AUTO_SELECT);
                    }
                    getPreferenceScreen().setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) log("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) log("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }
                    break;
                case EVENT_NETWOKK_DISCONNECT_DATA_DONE:
                    handleNetworkDisconnectDataDone(msg);
                    break;
                default:
                    if (DBG) log("unknown msg id: "+msg.what);
                    break;
            }

            return;
        }
    };

    /**
     * Service connection code for the NetworkQueryService.
     * Handles the work of binding to a local object so that we can make
     * the appropriate service calls.
     */

    /** Local service interface */
    private INetworkQueryService mNetworkQueryService = null;

    /** Service connection */
    private final ServiceConnection mNetworkQueryServiceConnection = new ServiceConnection() {

        /** Handle the task of binding the local object to the service */
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) log("connection created, binding local service.");
            mNetworkQueryService = ((NetworkQueryService.LocalBinder) service).getService();
            // as soon as it is bound, run a query.
            //loadNetworksList();
        }

        /** Handle the task of cleaning up the local binding */
        public void onServiceDisconnected(ComponentName className) {
            if (DBG) log("connection disconnected, cleaning local binding.");
            mNetworkQueryService = null;
        }
    };

    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            if (DBG) log("notifying message loop of query completion.");
            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED,
                    status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        boolean handled = false;

        if (preference == mSearchButton) {
            showDialog(DIALOG_NETWORK_DISCONNECT_DATA_CONFIRM);
            handled = true;
        } else if (preference == mAutoSelect) {
            selectNetworkAutomatic();
            handled = true;
        } else {
            Preference selectedCarrier = preference;

            String networkStr = selectedCarrier.getTitle().toString();
            if (DBG) log("selected network: " + networkStr);

            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
            mPhone.selectNetworkManually(mNetworkMap.get(selectedCarrier), msg);

            displayNetworkSeletionInProgress(networkStr);

            handled = true;
        }

        return handled;
    }

    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        if (mHandler.hasMessages(EVENT_SCAN_TIME_OUT))
            mHandler.removeMessages(EVENT_SCAN_TIME_OUT);
        MSimProxyManager.getInstance().enableDataConnectivity(MSimPhoneFactory.getDataSubscription());
        // request that the service stop the query with this callback object.
        try {
            mNetworkQueryService.stopNetworkQuery(mCallback);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        finish();
    }

    //implement DialogInterface.OnClickListener
    public void onClick(DialogInterface dialog,int which){
        if (which == DialogInterface.BUTTON_POSITIVE){
            if (DBG) log("do network search, disable data service first");
            showDialog(DIALOG_NETWORK_LIST_LOAD);
            //disable data service
            Message onCompleteMsg = mHandler.obtainMessage(EVENT_NETWOKK_DISCONNECT_DATA_DONE);
            MSimProxyManager.getInstance().disableDataConnectivity(MSimPhoneFactory.getDataSubscription(),onCompleteMsg);
        }else if (which == DialogInterface.BUTTON_NEGATIVE){
            if(DBG) log("network search,do nothing");
        }
    }

    private void handleNetworkDisconnectDataDone(Message msg){
        if(DBG) log("network disconnect data done");
        mNetworkSearchDataDisabled = true;
        loadNetworksList();
    }

    public String getNormalizedCarrierName(OperatorInfo ni) {
        if (ni != null) {
            return ni.getOperatorAlphaLong() + " (" + ni.getOperatorNumeric() + ")";
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.carrier_select);

        int subscription = getIntent().getIntExtra(SUBSCRIPTION_KEY, 0);
        log("onCreate subscription :" + subscription);
        mPhone = PhoneApp.getInstance().getPhone(subscription);
        Intent intent = new Intent(this, NetworkQueryService.class);
        intent.putExtra(SUBSCRIPTION_KEY, subscription);

        mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
        mNetworkMap = new HashMap<Preference, OperatorInfo>();

        mSearchButton = getPreferenceScreen().findPreference(BUTTON_SRCH_NETWRKS_KEY);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);

        // Start the Network Query service, and bind it.
        // The OS knows to start he service only once and keep the instance around (so
        // long as startService is called) until a stopservice request is made.  Since
        // we want this service to just stay in the background until it is killed, we
        // don't bother stopping it from our end.
        startService (intent);
        bindService (new Intent(this, NetworkQueryService.class), mNetworkQueryServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsForeground = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    /**
     * Override onDestroy() to unbind the query service, avoiding service
     * leak exceptions.
     */
    @Override
    protected void onDestroy() {
        // unbind the service.
        unbindService(mNetworkQueryServiceConnection);

        if (mHandler.hasMessages(EVENT_SCAN_TIME_OUT))
            mHandler.removeMessages(EVENT_SCAN_TIME_OUT);

        super.onDestroy();
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            ProgressDialog dialog = new ProgressDialog(this);
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    // It would be more efficient to reuse this dialog by moving
                    // this setMessage() into onPreparedDialog() and NOT use
                    // removeDialog().  However, this is not possible since the
                    // message is rendered only 2 times in the ProgressDialog -
                    // after show() and before onCreate.
                    dialog.setMessage(mNetworkSelectMsg);
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_AUTO_SELECT:
                    dialog.setMessage(getResources().getString(R.string.register_automatically));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_LIST_LOAD:
                    dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                    dialog.setCancelable(false);
                    dialog.setIndeterminate(true);
                    break;
                default:
                    // reinstate the cancelablity of the dialog.
                    //dialog.setMessage(getResources().getString(R.string.load_networks_progress));
                   // dialog.setCancelable(true);
                    //dialog.setOnCancelListener(this);
                    break;
            }
            return dialog;
        }

        if (id == DIALOG_NETWORK_DISCONNECT_DATA_CONFIRM){
            AlertDialog dialog = new AlertDialog.Builder(this)
                                     .setIcon(android.R.drawable.ic_dialog_alert)
                                     .setTitle(android.R.string.dialog_alert_title)
                                     .setMessage(R.string.disconnect_data_confirm)
                                     .setPositiveButton(android.R.string.ok,this)
                                     .setNegativeButton(android.R.string.no,this)
                                     .show();
            return dialog;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD) ||
                (id == DIALOG_NETWORK_AUTO_SELECT)) {
            // when the dialogs come up, we'll need to indicate that
            // we're in a busy state to dissallow further input.
            getPreferenceScreen().setEnabled(false);
        }
    }

    private void displayEmptyNetworkList(boolean flag) {
        mNetworkList.setTitle(flag ? R.string.empty_networks_list : R.string.label_available);
    }

    private void displayNetworkSeletionInProgress(String networkStr) {
        // TODO: use notification manager?
        mNetworkSelectMsg = getResources().getString(R.string.register_on_network, networkStr);

        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_SELECTION);
        }
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getResources().getString(R.string.network_query_error);

        final PhoneApp app = PhoneApp.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException) &&
                ((CommandException)ex).getCommandError()
                  == CommandException.Error.ILLEGAL_SIM_OR_ME)
        {
            status = getResources().getString(R.string.not_allowed);
        } else {
            status = getResources().getString(R.string.connect_later);
        }

        final PhoneApp app = PhoneApp.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionSucceeded() {
        String status = getResources().getString(R.string.registration_done);

        final PhoneApp app = PhoneApp.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        mHandler.postDelayed(new Runnable() {
            public void run() {
                finish();
            }
        }, 3000);
    }

    private void loadNetworksList() {
        if (DBG) log("load networks list...");


        // delegate query request to the service.
        try {
            mNetworkQueryService.startNetworkQuery(mCallback);
        } catch (RemoteException e) {
        }

        displayEmptyNetworkList(false);
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) log("networks list loaded");

        // update the state of the preferences.
        if (DBG) log("hideProgressPanel");

        try {
            dismissDialog(DIALOG_NETWORK_LIST_LOAD);
        } catch (IllegalArgumentException e){
            if (DBG) log(" DIALOG_NETWORK_LIST_LOAD dismissed already");
        }

        if (mHandler.hasMessages(EVENT_SCAN_TIME_OUT))
            mHandler.removeMessages(EVENT_SCAN_TIME_OUT);
        //enable data service
        MSimProxyManager.getInstance().enableDataConnectivity(MSimPhoneFactory.getDataSubscription());
        mNetworkSearchDataDisabled = false;

        getPreferenceScreen().setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) log("error while querying available networks");
            displayNetworkQueryFailed(status);
            displayEmptyNetworkList(true);
        } else {
            if (result != null){
                displayEmptyNetworkList(false);

                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                for (OperatorInfo ni : result) {
                    Preference carrier = new Preference(this, null);
                    carrier.setTitle(getNetworkTitle(ni));
                    carrier.setPersistent(false);
                    mNetworkList.addPreference(carrier);
                    mNetworkMap.put(carrier, ni);

                    if (DBG) log("  " + ni);
                }

            } else {
                displayEmptyNetworkList(true);
            }
        }
    }

    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param OperatorInfo contains the information of the network.
     *
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */

    private String getNetworkTitle(OperatorInfo ni) {
        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
            return ni.getOperatorAlphaLong();
        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
            return ni.getOperatorAlphaShort();
        } else {
            return ni.getOperatorNumeric();
        }
    }

    private void clearList() {
        for (Preference p : mNetworkMap.keySet()) {
            mNetworkList.removePreference(p);
        }
        mNetworkMap.clear();
    }

    private void selectNetworkAutomatic() {
        if (DBG) log("select network automatically...");
        if (mIsForeground) {
            showDialog(DIALOG_NETWORK_AUTO_SELECT);
        }

        Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
        mPhone.setNetworkSelectionModeAutomatic(msg);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }
}