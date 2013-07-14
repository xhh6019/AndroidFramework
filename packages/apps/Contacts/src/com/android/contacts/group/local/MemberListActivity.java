/*
 * Copyright (C) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

package com.android.contacts.group.local;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;

import android.app.TabActivity;
import android.content.AsyncQueryHandler;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TabHost;
import android.widget.TextView;

import com.android.contacts.R;

public class MemberListActivity extends TabActivity implements OnItemClickListener,
        OnClickListener, OnItemLongClickListener {

    private static final String TAB_TAG = "groups";

    private TabHost mTabHost;

    private Uri uri;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.LOOKUP_KEY,
            Data.RAW_CONTACT_ID
    };

    static final int SUMMARY_ID_COLUMN_INDEX = 0;

    static final int SUMMARY_DISPLAY_NAME_INDEX = 1;

    static final int SUMMARY_PHOTO_ID_INDEX = 2;

    static final int SUMMARY_LOOKUP_KEY_INDEX = 3;

    static final int SUMMARY_RAW_CONTACTS_ID_INDEX = 4;

    private static final int QUERY_TOKEN = 1;

    private QueryHandler mQueryHandler;

    private MemberListAdapter mAdapter;

    private ListView listView;

    private TextView emptyText;

    private Bundle removeSet;

    private View toolsBar;

    private Button deleteBtn;

    private Button cancelBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_manage);

        toolsBar = findViewById(R.id.tool_bar);
        deleteBtn = (Button) toolsBar.findViewById(R.id.btn_delete);
        cancelBtn = (Button) toolsBar.findViewById(R.id.btn_cancel);
        deleteBtn.setOnClickListener(this);
        cancelBtn.setOnClickListener(this);

        removeSet = new Bundle();
        mQueryHandler = new QueryHandler(this);
        mAdapter = new MemberListAdapter(this);
        emptyText = (TextView) findViewById(R.id.emptyText);
        listView = (ListView) findViewById(R.id.member_list);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
        listView.setAdapter(mAdapter);
        mTabHost = getTabHost();
        uri = getIntent().getParcelableExtra("data");
        addEditView();
        getContentResolver().registerContentObserver(
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(uri.getLastPathSegment())), true, observer);
    }

    private ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mAdapter.refresh();
        }
    };

    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        selectAll();
        return true;
    }

    private void updateDisplay(boolean isEmpty) {
        if (isEmpty) {
            listView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }
    }

    private void selectAll() {
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String contactId = String.valueOf(cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX));
            removeSet.putString(contactId, contactId);
        }
        mAdapter.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startQuery();
    }

    private void addEditView() {
        Intent intent = new Intent(this, GroupEditActivity.class);
        intent.setData(uri);

        mTabHost.addTab(mTabHost.newTabSpec(TAB_TAG)
                .setIndicator(TAB_TAG, getResources().getDrawable(R.drawable.ic_launcher_contacts))
                .setContent(intent));
    }

    private void startQuery() {
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        mQueryHandler.startQuery(
                QUERY_TOKEN,
                null,
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(uri.getLastPathSegment())), CONTACTS_SUMMARY_PROJECTION, null,
                null, null);
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<MemberListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MemberListActivity>((MemberListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final MemberListActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
            updateDisplay(cursor == null || cursor.getCount() == 0);
        }
    }

    private class MemberListAdapter extends CursorAdapter {

        public MemberListAdapter(Context context) {
            super(context, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
            String lookupId = cursor.getString(SUMMARY_LOOKUP_KEY_INDEX);
            long rawContactsId = cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX);
            String displayName = cursor.getString(SUMMARY_DISPLAY_NAME_INDEX);
            long photoId = cursor.getLong(SUMMARY_PHOTO_ID_INDEX);

            TextView contactNameView = (TextView) view.findViewById(R.id.contact_name);
            QuickContactBadge quickContactBadge = (QuickContactBadge) view
                    .findViewById(R.id.contact_icon);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);

            contactNameView
                    .setText(displayName == null ? getString(R.string.unknown) : displayName);
            quickContactBadge.setImageBitmap(getContactsPhoto(photoId));
            quickContactBadge.assignContactUri(Contacts.getLookupUri(contactId, lookupId));

            String key = String.valueOf(rawContactsId);
            setCheckStatus(key, checkBox);
            view.setTag(key);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.member_item, parent, false);
        }

        private void refresh() {
            super.onContentChanged();
            updateToolsBar();
            updateDisplay(this.getCount() == 0);
        }

    }

    public Bitmap getContactsPhoto(long photoId) {
        Bitmap contactPhoto = null;
        Cursor cursor = null;
        if (photoId > 0)
            try {
                cursor = getContentResolver().query(Data.CONTENT_URI, new String[] {
                        Photo.PHOTO
                }, Photo._ID + "=?", new String[] {
                        String.valueOf(photoId)
                }, null);

                if (cursor != null) {
                    if (cursor.moveToNext()) {
                        byte[] bytes = cursor.getBlob(0);
                        contactPhoto = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        if (contactPhoto == null) {
            contactPhoto = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.ic_contact_picture);
        }
        return framePhoto(contactPhoto);
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.ic_contact_picture);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top, width - padding.right, height
                - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return scaleToAppIconSize(b);
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {
        final int mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        // Setup the drawing classes
        Bitmap icon = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        String contactId = (String) arg1.getTag();
        CheckBox checkBox = (CheckBox) arg1.findViewById(R.id.pick_contact_check);
        if (removeSet.containsKey(contactId)) {
            removeSet.remove(contactId);
        } else {
            removeSet.putString(contactId, contactId);
        }
        setCheckStatus(contactId, checkBox);
        updateToolsBar();
    }

    private void updateToolsBar() {
        if (removeSet.isEmpty() && toolsBar.getVisibility() == View.VISIBLE) {
            toolsBar.setVisibility(View.GONE);
            toolsBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tools_bar_disappear));
        } else if (!removeSet.isEmpty() && toolsBar.getVisibility() == View.GONE) {
            toolsBar.setVisibility(View.VISIBLE);
            toolsBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tools_bar_appear));
        }
    }

    private void setCheckStatus(String contactId, CheckBox checkBox) {
        checkBox.setChecked(removeSet.containsKey(contactId));
    }

    @Override
    public void onClick(View v) {
        if (v == cancelBtn) {
            removeSet.clear();
            mAdapter.refresh();
        } else if (v == deleteBtn) {
            removeContactsFromGroup();
            mAdapter.refresh();
        }

    }

    private void removeContactsFromGroup() {
        if (removeSet != null && removeSet.size() > 0) {
            getContentResolver().delete(Data.CONTENT_URI, getWhere(removeSet),
                    getWhereArgs(removeSet));
            removeSet.clear();
        }
    }

    private String getWhere(Bundle result) {
        StringBuffer where = new StringBuffer();
        Set<String> keySet = result.keySet();
        Iterator<String> it = keySet.iterator();
        where.append("(");
        while (it.hasNext()) {
            it.next();
            where.append(Data.RAW_CONTACT_ID + " = ? OR ");
        }
        return where.substring(0, where.length() - 4) + ") and " + Data.MIMETYPE + "=?";
    }

    private String[] getWhereArgs(Bundle result) {
        String[] args = new String[result.size() + 1];
        Set<String> keySet = result.keySet();
        Iterator<String> it = keySet.iterator();
        int i = 0;
        while (it.hasNext()) {
            args[i++] = it.next();
        }
        args[i] = LocalGroup.CONTENT_ITEM_TYPE;
        return args;
    }
}
