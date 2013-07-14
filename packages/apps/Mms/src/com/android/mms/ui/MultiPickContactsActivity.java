/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
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

package com.android.mms.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.R;

public class MultiPickContactsActivity extends ListActivity implements
        View.OnClickListener, TextView.OnEditorActionListener,
        OnTouchListener, TextWatcher {
    private final static String TAG = "MultiPickContactsActivity";
    private final static boolean DEBUG = true;

    public static final String RESULT_KEY = "result";

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, // 0
        Phone.NUMBER, // 1
        Phone.DISPLAY_NAME // 2
    };

    static final String PHONES_SELECTION = RawContacts.ACCOUNT_TYPE + "<>?" ;
    static final String[] PHONES_SELECTION_ARGS = {"com.android.sim"};

    private static final int PHONE_COLUMN_ID = 0;
    private static final int PHONE_COLUMN_NUMBER = 1;
    private static final int PHONE_COLUMN_DISPLAY_NAME = 2;
    private static final int QUERY_TOKEN = 42;
    private static final int MODE_MASK_SEARCH = 0x80000000;

    private static final int MODE_DEFAULT_PHONE = 1;
    private static final int MODE_SEARCH_PHONE = MODE_DEFAULT_PHONE | MODE_MASK_SEARCH;

    static final String ACTION_MULTI_PICK = "com.android.contacts.action.GET_MULTIPLE_PHONES";


    private ContactItemListAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private ArrayList<Uri> mChoiceUris = new ArrayList<Uri>();
    private ArrayList<Uri> mBackupChoiceUris;
    private EditText mSearchEditor;
    private Button mOKButton;
    private Button mCancelButton;
    private TextView mSelectAllLabel;
    private CheckBox mSelectAllCheckBox;
    private int mMode;

    private ProgressDialog mProgressDialog;
    private static final int SHOW_DIALOG_DELAY = 1000;

    private String mExsitNumbers;

    /**
     * control of whether show the contacts in SIM card, if intent has this
     * flag,not show.
     */
    public static final String EXT_NOT_SHOW_SIM_FLAG = "not_sim_show";

    private static final int MSG_UPDATE_STATUS = 1;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_STATUS:
                    updateContent();
                    break;
                default:
                    break;
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        Intent intent = getIntent();
        String action = intent.getAction();
        if (ACTION_MULTI_PICK.equals(action)) {
            mMode = MODE_DEFAULT_PHONE;
        }

        this.setContentView(R.layout.pick_contact);
        initResource();

        mQueryHandler = new QueryHandler(this);
        Bundle b = getIntent().getExtras();
        // Check if it has inputed numbers, we will not show exist numbers again.
        if (b != null) {
            mExsitNumbers = b.getString(Intents.EXTRA_PHONE_URIS);
            Log.i(TAG,"onCreate() : mExsitNumbers="+mExsitNumbers);
        }
// delete annotation 2013.5.20
        Parcelable[] uris =
                intent.getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);
        if (uris != null) {
            getCheckedContacts(uris);
        	}
// delete annotation 2013.5.20
        mAdapter = new ContactItemListAdapter(this);
        getListView().setAdapter(mAdapter);

        startQuery();
    }

    private boolean isSearchMode() {
        return (mMode & MODE_MASK_SEARCH) == MODE_MASK_SEARCH;
    }

    private void initResource() {
        mOKButton = (Button) findViewById(R.id.btn_ok);
        mOKButton.setOnClickListener(this);
        mOKButton.setText(getOKString());
        mCancelButton = (Button) findViewById(R.id.btn_cancel);
        mCancelButton.setOnClickListener(this);
        mSearchEditor = ((EditText) findViewById(R.id.search_field));
        mSearchEditor.addTextChangedListener(this);

        mSearchEditor.setOnClickListener(this);
        mSearchEditor.setOnTouchListener(this);
        mSearchEditor.setOnEditorActionListener(this);
        mSelectAllCheckBox = (CheckBox) findViewById(R.id.select_all_check);
        mSelectAllCheckBox.setOnClickListener(this);
        mSelectAllLabel = (TextView) findViewById(R.id.select_all_label);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();
        CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
        boolean isChecked = checkBox.isChecked() ? false : true;
        checkBox.setChecked(isChecked);

        ContactItemCache cache = (ContactItemCache) v.getTag();
        Uri uri = ContentUris.withAppendedId(Phone.CONTENT_URI, cache.id);

        if (isChecked) {
            mChoiceUris.add(uri);
            if (!isSearchMode()) {
                if (mChoiceUris.size() == mAdapter.getCount()) {
                    mSelectAllCheckBox.setChecked(true);
                }
            }
        } else {
            mChoiceUris.remove(uri);
            mSelectAllCheckBox.setChecked(false);
        }
        mOKButton.setText(getOKString());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK: {
                if (isSearchMode()) {
                    exitSearchMode(false);
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private String getOKString() {
        return getString(R.string.btn_ok) + "(" + mChoiceUris.size() + ")";
    }

    private void backupChoiceSet() {
        mBackupChoiceUris = (ArrayList<Uri>) mChoiceUris.clone();
    }

    private void restoreChoiceSet() {
        mChoiceUris = mBackupChoiceUris;
    }

    private void enterSearchMode() {
        mMode |= MODE_MASK_SEARCH;
        mSelectAllLabel.setVisibility(View.GONE);
        mSelectAllCheckBox.setVisibility(View.GONE);
        backupChoiceSet();
    }

    private void exitSearchMode(boolean isConfirmed) {
        mMode &= ~MODE_MASK_SEARCH;
        hideSoftKeyboard();
        mSelectAllLabel.setVisibility(View.VISIBLE);
        mSelectAllCheckBox.setVisibility(View.VISIBLE);
        if (!isConfirmed) restoreChoiceSet();
        mSearchEditor.setText("");
        mOKButton.setText(getOKString());
    }

    public void onClick(View v) {
        int id = v.getId();
        switch(id) {
        case R.id.btn_ok:
            if (!isSearchMode()) {
                    Intent intent = new Intent();
                    Uri[] uris = mChoiceUris.toArray(new Uri[0]);
                    Parcelable[] p = (Parcelable[])(uris);
                    intent.putExtra(Intents.EXTRA_PHONE_URIS, p);
                    this.setResult(RESULT_OK, intent);
                    finish();
            } else {
                exitSearchMode(true);
            }
            break;
        case R.id.btn_cancel:
            if (!isSearchMode()) {
                this.setResult(this.RESULT_CANCELED);
                finish();
            } else {
                exitSearchMode(false);
            }
            break;
        case R.id.select_all_check:
            if (mSelectAllCheckBox.isChecked()) {
                selectAll(true);
            } else {
                selectAll(false);
            }
            break;
        case R.id.search_field:
            enterSearchMode();
            break;
        }
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);
        if (mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }

        super.onDestroy();
    }

    private Uri getUriToQuery() {
        switch (mMode) {
            case MODE_DEFAULT_PHONE:
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_URI;

            default:
                Log.w(TAG, "getUriToQuery: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_URI;
    }

    private Uri getFilterUri() {
        switch (mMode) {
            case MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            default:
                Log.w(TAG, "getFilterUri: Incorrect mode: " + mMode);
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    private String[] getProjectionForQuery() {
        return PHONES_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        return "sort_key";
    }

    private String getSelectionForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_PHONE:
                if (isShowSIM()) {
                    if (!TextUtils.isEmpty(mExsitNumbers)) {
                        return Phone.NUMBER + " NOT IN ("+mExsitNumbers+")";
                    } else {
                        return null;
                    }
                }
                else
                    return PHONES_SELECTION;
            default:
                return null;
        }
    }

    private String[] getSelectionArgsForQuery() {
        switch (mMode) {
            case MODE_DEFAULT_PHONE:
                if (isShowSIM())
                    return null;
                else
                    return PHONES_SELECTION_ARGS;
            default:
                return null;
        }
    }

    private boolean isShowSIM(){
        return !getIntent().hasExtra(EXT_NOT_SHOW_SIM_FLAG);
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    public void afterTextChanged(Editable s) {
        if (!TextUtils.isEmpty(s)) {
            if (!isSearchMode()) {
                enterSearchMode();
            }
        }else if(isSearchMode()){
            exitSearchMode(true);
        }
        doFilter(s);
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void doFilter(Editable s) {
        if (TextUtils.isEmpty(s)) {
            startQuery();
            return;
        }
        Uri uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(s.toString()));
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForQuery();
        String[] selectionArgs = getSelectionArgsForQuery();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection,
                selectionArgs, getSortOrder(projection));
    }

    public void updateContent() {
        if (isSearchMode()) {
            doFilter(mSearchEditor.getText());
        } else {
            startQuery();
        }
        mOKButton.setText(getOKString());
    }

    private boolean isPickPhone() {
        return mMode == MODE_DEFAULT_PHONE || mMode == MODE_SEARCH_PHONE;
    }

    private void selectAll(boolean isSelected) {
        // update mContactList.
        // TODO: make it more efficient
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
            Log.w(TAG, "cursor is null.");
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            long id = 0;
            id = cursor.getLong(PHONE_COLUMN_ID);
            Uri uri = ContentUris.withAppendedId(Phone.CONTENT_URI, id);
            if (DEBUG) Log.d(TAG, "isSelected: " + isSelected + ", id: " + id);
            if (isSelected) {
                if (!mChoiceUris.contains(uri)) {
                    mChoiceUris.add(uri);
                }
            } else {
                mChoiceUris.remove(uri);
            }
        }

        // update UI items.
        mOKButton.setText(getOKString());
        updateContent();
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<MultiPickContactsActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MultiPickContactsActivity>((MultiPickContactsActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final MultiPickContactsActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
        }
    }

    private final class ContactItemCache {
        long   id;
        String name;
        String number;
    }

    private final class ContactItemListAdapter extends CursorAdapter {
        Context mContext;
        protected LayoutInflater mInflater;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            //TODO
            mContext = context;
            mInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            cache.id = cursor.getLong(PHONE_COLUMN_ID);
            cache.name = cursor.getString(PHONE_COLUMN_DISPLAY_NAME);
            cache.number = cursor.getString(PHONE_COLUMN_NUMBER);
            ((TextView) view.findViewById(R.id.pick_contact_name)).setText(cache.name);
            ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);

            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);
            if (mChoiceUris.contains(ContentUris.withAppendedId(Phone.CONTENT_URI, cache.id))) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = mInflater.inflate(R.layout.pick_contact_item, parent, false);
            ContactItemCache cache = new ContactItemCache();
            v.setTag(cache);
            return v;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;

            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException(
                        "couldn't move cursor to position " + position);
            }
            if (convertView != null && convertView.getTag() != null) {
                v = convertView;
            } else {
                v = newView(mContext, mCursor, parent);
            }
            bindView(v, mContext, mCursor);
            return v;
        }

        @Override
        protected void onContentChanged() {
            updateContent();
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            if (!isSearchMode()) {
                if (cursor != null && cursor.getCount() != 0 && cursor.getCount() == mChoiceUris.size()) {
                    mSelectAllCheckBox.setChecked(true);
                } else {
                    mSelectAllCheckBox.setChecked(false);
                }
            }
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            Log.d(TAG, "onEditorAction:hide soft keyboard");
            return true;
        }
        Log.d(TAG, "onEditorAction");
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchEditor.getWindowToken(), 0);
    }

    private String getTextFilter() {
        if (mSearchEditor != null) {
            return mSearchEditor.getText().toString();
        }
        return null;
    }

    private void getCheckedContacts(Parcelable[] data){
        final Parcelable[] uris = data;
        final Handler handler = new Handler();
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getText(R.string.pick_too_many_recipients));
        progressDialog.setMessage(getText(R.string.adding_recipients));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        final Runnable showProgress = new Runnable() {
            public void run() {
                progressDialog.show();
            }
        };
        // Only show the progress dialog if we can not finish off parsing the return data in 1s,
        // otherwise the dialog could flicker.
        handler.postDelayed(showProgress, SHOW_DIALOG_DELAY);

        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Uri> list;
                try {
                    list = getSelectedUris(uris);
                } finally {
                    handler.removeCallbacks(showProgress);
                    progressDialog.dismiss();
                }
                updateCheckStatus(list);
            }
        }).start();
    }

    private ArrayList<Uri> getSelectedUris (Parcelable[] uris) {
        ArrayList<Uri> selectedUris = new ArrayList<Uri>();
        for (Parcelable p : uris) {
            Uri uri = (Uri) p;
            selectedUris.add(uri);
        }
        return selectedUris;
    }

    private void updateCheckStatus(ArrayList<Uri> contactList){
        if (mChoiceUris.size() == 0) {
            mChoiceUris = contactList;
        } else {
            mChoiceUris.addAll(contactList);
            
        }
        mHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
    }

}
