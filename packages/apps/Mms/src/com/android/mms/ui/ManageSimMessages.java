/*
 * Copyright (C) 2010-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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
import com.android.mms.data.Contact;
import android.database.sqlite.SqliteWrapper;
import com.android.mms.transaction.MessagingNotification;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.telephony.TelephonyManager;


import com.android.internal.telephony.MSimConstants;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class ManageSimMessages extends Activity
        implements View.OnCreateContextMenuListener {
    private static final Uri ICC_URI = Uri.parse("content://sms/icc");
    private static final Uri ICC1_URI = Uri.parse("content://sms/icc1");
    private static final Uri ICC2_URI = Uri.parse("content://sms/icc2");
    private static final String TAG = "ManageSimMessages";
    private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
    private static final int MENU_DELETE_FROM_SIM = 1;
    private static final int MENU_VIEW = 2;
    private static final int MENU_REPLY = 3;
    private static final int MENU_FORWARD = 4;
    private static final int MENU_CALL_BACK = 5;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 6;
    private static final int MENU_SEND_EMAIL = 7;
    private static final int OPTION_MENU_DELETE_ALL = 0;

    private static final int SUB_INVALID = -1;
    private static final int SUB1 = 0;
    private static final int SUB2 = 1;

    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;

    private Uri mIccUri;
    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private ListView mSimList;
    private TextView mMessage;
    private MessageListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;

    private boolean mIsDeleteAll = false;

    public static final int SIM_FULL_NOTIFICATION_ID = 234;

    private final ContentObserver simChangeObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            refreshMessageList();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        int subscription = getIntent().getIntExtra(MSimConstants.SUBSCRIPTION_KEY, SUB_INVALID);
        mIccUri = getIccUriBySubscription(subscription);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.sim_list);
        mSimList = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);
	 mMessage.setText(setSimUimString(R.string.sim_empty));
        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        init();
    }
	
    private String setSimUimString(int id){
	       String SimString = getString(id) ;
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

    private void init() {
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);

        updateState(SHOW_BUSY);
        startQuery();
    }

    private Uri getIccUriBySubscription(int subscription) {
        switch (subscription) {
            case MSimConstants.SUB1:
                return ICC1_URI;
            case MSimConstants.SUB2:
                return ICC2_URI;
            default:
                return ICC_URI;
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        private final ManageSimMessages mParent;

        public QueryHandler(
                ContentResolver contentResolver, ManageSimMessages parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(
                int token, Object cookie, Cursor cursor) {
            mCursor = cursor;
            if (mCursor != null) {
                if (!mCursor.moveToFirst()) {
                    // Let user know the SIM is empty
                    updateState(SHOW_EMPTY);
                } else if (mListAdapter == null) {
                    // Note that the MessageListAdapter doesn't support auto-requeries. If we
                    // want to respond to changes we'd need to add a line like:
                    //   mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
                    // See ComposeMessageActivity for an example.
                    mListAdapter = new MessageListAdapter(
                            mParent, mCursor, mSimList, false, null);
                    mSimList.setAdapter(mListAdapter);
                    mSimList.setOnCreateContextMenuListener(mParent);
                    updateState(SHOW_LIST);
                } else {
                    mListAdapter.changeCursor(mCursor);
                    updateState(SHOW_LIST);
                }
                startManagingCursor(mCursor);
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
        }
    }

    private void startQuery() {
        try {
            mQueryHandler.startQuery(0, null, mIccUri, null, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        if (mCursor != null) {
            stopManagingCursor(mCursor);
            mCursor.close();
        }
        startQuery();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0, MENU_COPY_TO_PHONE_MEMORY, 0,
                 R.string.sim_copy_to_phone_memory);
        menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);

        Cursor cursor = mListAdapter.getCursor();
        if (isIncomingMessage(cursor)) {
            String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", address, null));
            menu.add(0, MENU_REPLY, 0, R.string.menu_reply).setIntent(intent);
        }
        menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
        addCallAndContactMenuItems(menu, cursor);

        // TODO: Enable this once viewMessage is written.
        // menu.add(0, MENU_VIEW, 0, R.string.sim_view);
    }

    // refs to ComposeMessageActivity.java
    private final void addCallAndContactMenuItems(
            ContextMenu menu, Cursor cursor) {
        String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        // Add all possible links in the address & message
        StringBuilder textToSpannify = new StringBuilder();
        if (address != null) {
            textToSpannify.append(address + ": ");
        }
        textToSpannify.append(body);

        SpannableString msg = new SpannableString(textToSpannify.toString());
        Linkify.addLinks(msg, Linkify.ALL);
        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));

        while (uris.size() > 0) {
            String uriString = uris.remove(0);
            // Remove any dupes so they don't get added to the menu multiple times
            while (uris.contains(uriString)) {
                uris.remove(uriString);
            }

            int sep = uriString.indexOf(":");
            String prefix = null;
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            boolean addToContacts = false;
            if ("mailto".equalsIgnoreCase(prefix))  {
                String sendEmailString = getString(
                        R.string.menu_send_email).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("mailto:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                menu.add(0, MENU_SEND_EMAIL, 0, sendEmailString)
                    .setIntent(intent);
                addToContacts = !haveEmailContact(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                String callBackString = getString(
                        R.string.menu_call_back).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_CALL,
                        Uri.parse("tel:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                menu.add(0, MENU_CALL_BACK, 0, callBackString)
                    .setIntent(intent);
                addToContacts = !isNumberInContacts(uriString);
            }
            if (addToContacts) {
                Intent intent = ConversationList.createAddContactIntent(uriString);
                String addContactString = getString(
                        R.string.menu_add_address_to_contacts).replace("%s", uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setIntent(intent);
            }
        }
    }

    private boolean haveEmailContact(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (!TextUtils.isEmpty(name)) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    private boolean isNumberInContacts(String phoneNumber) {
        return Contact.get(phoneNumber, false).existsInDatabase();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException exception) {
            Log.e(TAG, "Bad menuInfo.", exception);
            return false;
        }

        final Cursor cursor = (Cursor) mListAdapter.getItem(info.position);

        switch (item.getItemId()) {
            case MENU_COPY_TO_PHONE_MEMORY:
                copyToPhoneMemory(cursor);
                return true;
            case MENU_DELETE_FROM_SIM:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        deleteFromSim(cursor);
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_SIM_message);
                return true;
            case MENU_FORWARD: {
                String smsBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                forwardMessage(smsBody);
                return true;
            }
            case MENU_VIEW:
                viewMessage(cursor);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void forwardMessage(String smsBody) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);

        intent.putExtra("exit_on_sent", true);
        intent.putExtra("forwarded_message", true);

        intent.putExtra("sms_body", smsBody);
        intent.setClassName(this, "com.android.mms.ui.ForwardMessageActivity");
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerSimChangeObserver();
    }

    @Override
    public void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(simChangeObserver);
    }

    @Override
    public void onBackPressed() {
        if (mIsDeleteAll) {
            mIsDeleteAll = false;
        } else {
            super.onBackPressed();
        }
    }

    private void registerSimChangeObserver() {
        mContentResolver.registerContentObserver(
                mIccUri, true, simChangeObserver);
    }

    private void copyToPhoneMemory(Cursor cursor) {
        String address = cursor.getString(
                cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        Long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

        try {
            if (isIncomingMessage(cursor)) {
                Sms.Inbox.addMessage(mContentResolver, address, body, null, date, true /* read */);
            } else {
                Sms.Sent.addMessage(mContentResolver, address, body, null, date);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private boolean isIncomingMessage(Cursor cursor) {
        int messageStatus = cursor.getInt(
                cursor.getColumnIndexOrThrow("status"));

        return (messageStatus == SmsManager.STATUS_ON_ICC_READ) ||
               (messageStatus == SmsManager.STATUS_ON_ICC_UNREAD);
    }

    private void deleteFromSim(Cursor cursor) {
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        Uri simUri = mIccUri.buildUpon().appendPath(messageIndexString).build();
        SqliteWrapper.delete(this, mContentResolver, simUri, null, null);
    }

    private void deleteAllFromSim() {
        mIsDeleteAll = true;
        Cursor cursor = (Cursor) mListAdapter.getCursor();

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mContentResolver.unregisterContentObserver(simChangeObserver);
                int count = cursor.getCount();

                for (int i = 0; i < count; ++i) {
                    if (!mIsDeleteAll) {
                        break;
                    }
                    deleteFromSim(cursor);
                    cursor.moveToNext();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshMessageList();
                        registerSimChangeObserver();
                    }
                });
            }
        }

        mIsDeleteAll = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if ((null != mCursor) && (mCursor.getCount() > 0) && mState == SHOW_LIST) {
            menu.add(0, OPTION_MENU_DELETE_ALL, 0, R.string.menu_delete_messages).setIcon(
                    android.R.drawable.ic_menu_delete);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_DELETE_ALL:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        new Thread() {
                            @Override
                            public void run() {
                                deleteAllFromSim();
                            }
                        }.start();
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_all_SIM_messages);
                break;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(setSimUimString(messageId));

        builder.show();
    }

    private String ManageMessageTitle(){
		if(TelephonyManager.getDefault().isMultiSimEnabled() ){
		return getString(R.string.sim_uim_manage_messages_title);
		}else{
		return getString(R.string.uim_manage_messages_title);
		}
    	}
    private void updateState(int state) {
        if (mState == state) {
            return;
        }

        mState = state;
        switch (state) {
            case SHOW_LIST:
                mSimList.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.GONE);
                setTitle(ManageMessageTitle());
                setProgressBarIndeterminateVisibility(false);
                mSimList.requestFocus();
                break;
            case SHOW_EMPTY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                setTitle(ManageMessageTitle());
                setProgressBarIndeterminateVisibility(false);
                break;
            case SHOW_BUSY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.refreshing));
                setProgressBarIndeterminateVisibility(true);
                break;
            default:
                Log.e(TAG, "Invalid State");
        }
    }

    private void viewMessage(Cursor cursor) {
        // TODO: Add this.
    }
}

