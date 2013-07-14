/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.calllog;

import com.android.common.io.MoreCloseables;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.activities.DialtactsActivity.ViewPagerVisibilityListener;
import com.android.contacts.util.EmptyLoader;
import com.android.contacts.voicemail.VoicemailStatusHelper;
import com.android.contacts.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.contacts.voicemail.VoicemailStatusHelperImpl;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;
import com.google.common.annotations.VisibleForTesting;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.provider.CallLog.Calls;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.List;

/**
 * Displays a list of call log entries.
 */
public class CallLogFragment extends ListFragment implements ViewPagerVisibilityListener,
        CallLogQueryHandler.Listener, CallLogAdapter.CallFetcher {
    private static final String TAG = "CallLogFragment";

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;

    private boolean mShowOptionsMenu;
    /** Whether there is at least one voicemail source installed. */
    private boolean mVoicemailSourcesAvailable = false;
    /** Whether we are currently filtering over voicemail. */
    private boolean mShowingVoicemailOnly = false;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private KeyguardManager mKeyguardManager;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;

    private RadioButton allCallTypeBut;
    private RadioButton inCallTypeBut;
    private RadioButton outCallTypeBut;
    private RadioButton missCallTypeBut;

    private ImageView slotList;
    private ImageView slotSelect;

    private static final int TYPE_INDEX_ALL = 0;

    private OnClickListener callTypeListener = new OnClickListener(){
        @Override
        public void onClick(View v) {
            int callType = TYPE_INDEX_ALL;
            switch(v.getId()){
                case R.id.call_in:
                    callType = Calls.INCOMING_TYPE;
                    break;
                case R.id.call_out:
                    callType = Calls.OUTGOING_TYPE;
                    break;
                case R.id.call_miss:
                    callType = Calls.MISSED_TYPE;
                    break;
                default:
                    callType = TYPE_INDEX_ALL;
                    break;
            }
            mCallLogQueryHandler.setCallType(callType);
            refreshData();
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(), this);
        mKeyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        setHasOptionsMenu(true);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();
        if (mScrollToTop) {
            final ListView listView = getListView();
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            listView.smoothScrollToPosition(0);
            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        updateVoicemailStatusMessage(statusCursor);

        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        setVoicemailSourcesAvailable(activeSources != 0);
        MoreCloseables.closeQuietly(statusCursor);
        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    /** Sets whether there are any voicemail sources available in the platform. */
    private void setVoicemailSourcesAvailable(boolean voicemailSourcesAvailable) {
        if (mVoicemailSourcesAvailable == voicemailSourcesAvailable) return;
        mVoicemailSourcesAvailable = voicemailSourcesAvailable;

        Activity activity = getActivity();
        if (activity != null) {
            // This is so that the options menu content is updated.
            activity.invalidateOptionsMenu();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);

        allCallTypeBut = (RadioButton) view.findViewById(R.id.call_all);
        inCallTypeBut = (RadioButton) view.findViewById(R.id.call_in);
        outCallTypeBut = (RadioButton) view.findViewById(R.id.call_out);
        missCallTypeBut = (RadioButton) view.findViewById(R.id.call_miss);
        allCallTypeBut.setOnClickListener(callTypeListener);
        inCallTypeBut.setOnClickListener(callTypeListener);
        outCallTypeBut.setOnClickListener(callTypeListener);
        missCallTypeBut.setOnClickListener(callTypeListener);

        slotList = (ImageView) view.findViewById(R.id.slot_list);
        slotSelect = (ImageView) view.findViewById(R.id.slot_select);
        if (!TelephonyManager.getDefault().isMultiSimEnabled()) {
            view.findViewById(R.id.slot_select_container).setVisibility(View.GONE);
        }
        updateSubImage();

        slotList.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View v) {
                showSlotChangeDialog();
            }
        });
        slotList.setOnTouchListener(new OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction() & MotionEvent.ACTION_MASK;
                switch (action) {
                    case MotionEvent.ACTION_DOWN: {
                        slotSelect.setImageResource(R.drawable.ic_tab_sim_select_touch);
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        slotSelect.setImageResource(R.drawable.ic_tab_sim_select);
                        break;
                }
                return false;
            }});
        return view;
    }

    protected void showSlotChangeDialog() {
        new AlertDialog.Builder(this.getActivity()).setSingleChoiceItems(
                new MultiSlotAdapter(this.getActivity()), 0, slotListener).setTitle(
                R.string.title_slot_change).create().show();
    }

    private DialogInterface.OnClickListener slotListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            int sub = -1;
            if (which >= TelephonyManager.getDefault().getPhoneCount())
                sub = -1;
            else
                sub = which;
            saveSlot(sub);
            updateSubImage();
            mCallLogQueryHandler.setSubscription(sub);
            refreshData();
        }
    };

    private void updateSubImage(){
        int sub = getSlot();
        switch(sub){
            case -1:
                slotList.setImageResource(R.drawable.ic_tab_sim12);
                break;
            case 0:
                slotList.setImageResource(R.drawable.ic_tab_sim1);
                break;
            case 1:
                slotList.setImageResource(R.drawable.ic_tab_sim2);
                break;
        }

    }

    private void saveSlot(int slot) {
        PreferenceManager.getDefaultSharedPreferences(this.getActivity()).edit().putInt("Subscription", slot).commit();
    }

    private int getSlot() {
        return PreferenceManager.getDefaultSharedPreferences(this.getActivity()).getInt("Subscription", -1);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String currentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());
        mAdapter = new CallLogAdapter(getActivity(), this,
                new ContactInfoHelper(getActivity(), currentCountryIso));
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);
    }

    @Override
    public void onStart() {
        mScrollToTop = true;

        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null,
                new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mCallLogQueryHandler.setSubscription(getSlot());
        refreshData();
    }

    private void updateVoicemailStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            mStatusMessageView.setVisibility(View.GONE);
        } else {
            mStatusMessageView.setVisibility(View.VISIBLE);
            // TODO: Change the code to show all messages. For now just pick the first message.
            final StatusMessage message = messages.get(0);
            if (message.showInCallLog()) {
                mStatusMessageText.setText(message.callLogMessageId);
            }
            if (message.actionMessageId != -1) {
                mStatusMessageAction.setText(message.actionMessageId);
            }
            if (message.actionUri != null) {
                mStatusMessageAction.setVisibility(View.VISIBLE);
                mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, message.actionUri));
                    }
                });
            } else {
                mStatusMessageAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onStop() {
        super.onStop();
        updateOnExit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
    }

    @Override
    public void fetchCalls() {
        if (mShowingVoicemailOnly) {
            mCallLogQueryHandler.fetchVoicemailOnly();
        } else {
            mCallLogQueryHandler.fetchAllCalls();
        }
    }

    public void startCallsQuery() {
        mAdapter.setLoading(true);
        mCallLogQueryHandler.fetchAllCalls();
        if (mShowingVoicemailOnly) {
            mShowingVoicemailOnly = false;
            getActivity().invalidateOptionsMenu();
        }
    }

    private void startVoicemailStatusQuery() {
        mCallLogQueryHandler.fetchVoicemailStatus();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mShowOptionsMenu) {
            inflater.inflate(R.menu.call_log_options, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (mShowOptionsMenu) {
            final MenuItem itemDeleteAll = menu.findItem(R.id.delete_all);
            // Check if all the menu items are inflated correctly. As a shortcut, we assume all
            // menu items are ready if the first item is non-null.
            if (itemDeleteAll != null) {
                itemDeleteAll.setEnabled(mCallLogQueryHandler.hasCallLog());
                menu.findItem(R.id.show_voicemails_only).setVisible(
                        mVoicemailSourcesAvailable && !mShowingVoicemailOnly);
                menu.findItem(R.id.show_all_calls).setVisible(
                        mVoicemailSourcesAvailable && mShowingVoicemailOnly);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete_all:
//                ClearCallLogDialog.show(getFragmentManager());
                onDelCallLog();
                return true;

            case R.id.show_voicemails_only:
                mCallLogQueryHandler.fetchVoicemailOnly();
                mShowingVoicemailOnly = true;
                return true;

            case R.id.show_all_calls:
                mCallLogQueryHandler.fetchAllCalls();
                mShowingVoicemailOnly = false;
                return true;

            default:
                return false;
        }
    }

    private void onDelCallLog(){
        Intent intent = new Intent("com.android.contacts.action.MULTI_PICK_CALL");
        startActivity(intent);
    }

    public void callSelectedEntry() {
        int position = getListView().getSelectedItemPosition();
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor != null) {
            String number = cursor.getString(CallLogQuery.NUMBER);
            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)
                    || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }
            Intent intent;
            // If "number" is really a SIP address, construct a sip: URI.
            if (PhoneNumberUtils.isUriNumber(number)) {
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("sip", number, null));
            } else {
                // We're calling a regular PSTN phone number.
                // Construct a tel: URI, but do some other possible cleanup first.
                int callType = cursor.getInt(CallLogQuery.CALL_TYPE);
                if (!number.startsWith("+") &&
                       (callType == Calls.INCOMING_TYPE
                                || callType == Calls.MISSED_TYPE)) {
                    // If the caller-id matches a contact with a better qualified number, use it
                    String countryIso = cursor.getString(CallLogQuery.COUNTRY_ISO);
                    number = mAdapter.getBetterNumberFromContacts(number, countryIso);
                }
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("tel", number, null));
            }
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @VisibleForTesting
    CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
        if (mShowOptionsMenu != visible) {
            mShowOptionsMenu = visible;
            // Invalidate the options menu since we are changing the list of options shown in it.
            Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
            }
        }

        if (visible && isResumed()) {
            refreshData();
        }

        if (!visible) {
            updateOnExit();
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Mark all entries in the contact info cache as out of date, so they will be looked up
        // again once being shown.
        mAdapter.invalidateCache();
        startCallsQuery();
        startVoicemailStatusQuery();
        updateOnEntry();
    }

    /** Removes the missed call notifications. */
    private void removeMissedCallNotifications() {
        try {
            ITelephony telephony =
                    ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
            if (telephony != null) {
                telephony.cancelMissedCallsNotification();
            } else {
                Log.w(TAG, "Telephony service is null, can't call " +
                        "cancelMissedCallsNotification");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
        }
    }

    /** Updates call data and notification state while leaving the call log tab. */
    private void updateOnExit() {
        updateOnTransition(false);
    }

    /** Updates call data and notification state while entering the call log tab. */
    private void updateOnEntry() {
        updateOnTransition(true);
    }

    private void updateOnTransition(boolean onEntry) {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        if (!mKeyguardManager.inKeyguardRestrictedInputMode()) {
            // On either of the transitions we reset the new flag and update the notifications.
            // While exiting we additionally consume all missed calls (by marking them as read).
            // This will ensure that they no more appear in the "new" section when we return back.
            mCallLogQueryHandler.markNewCallsAsOld();
            if (!onEntry) {
                mCallLogQueryHandler.markMissedCallsAsRead();
            }
            removeMissedCallNotifications();
            updateVoicemailNotifications();
        }
    }

    private void updateVoicemailNotifications() {
        Intent serviceIntent = new Intent(getActivity(), CallLogNotificationsService.class);
        serviceIntent.setAction(CallLogNotificationsService.ACTION_UPDATE_NOTIFICATIONS);
        getActivity().startService(serviceIntent);
    }
}
