/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.music;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TabWidget;

import android.net.Uri;
import android.os.Handler;
import android.os.Environment;
import android.content.Context;
import android.widget.TextView;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import java.lang.Integer;

public class VideoBrowserActivity extends ListActivity implements MusicUtils.Defs
{
    private String mFilterString = "";

    public VideoBrowserActivity()
    {
    }

    Handler mHandler = new Handler();

    Runnable mScanningCheck = new Runnable() {
        public void run() {
            MakeCursor();
            if (mExternalCursor == null) {
                if (mScanErrorCounter != 60) {
                    ++mScanErrorCounter;
                    mHandler.postDelayed(mScanningCheck, 1000);
                } else {
                    displayDatabaseError(true);
                }
            } else if (mExternalCursor.getCount() > 0) {
                setTitle(R.string.videos_title);
                initAdapter();
            } else {
                mHandler.postDelayed(mScanningCheck, 1000);
            }
        }
    };

    private void initAdapter() {
        MusicUtils.hideDatabaseError(this);
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_1,
                mExternalCursor,
                new String[] { MediaStore.Video.Media.DISPLAY_NAME},
                new int[] { android.R.id.text1 });

		setListAdapter(adapter);
	}

    private void onCheckAction(String action, Uri uri) {
        String Suri = uri.toString();

        if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
            if (uri.toString().contains("mnt/sdcard")) {
                setTitle(R.string.scanning_sdcard_title);
                TextView tv = (TextView) this.findViewById(R.id.sd_message);
                tv.setText(R.string.scanning_sdcard_message);
                mScanErrorCounter = 0;
                mHandler.postDelayed(mScanningCheck, 1000);
                Intent intent = new Intent();
                intent.setClass(this, ScanningProgress.class);
                startActivity(intent);
            } else if (uri.toString().contains("system/media")) {
                setTitle(R.string.scanning_system_title);
                TextView tv = (TextView) this.findViewById(R.id.sd_message);
                tv.setText(R.string.scanning_system_message);
            }
        } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
            if (uri.toString().contains("mnt/sdcard")) {
                MakeCursor();
                if (mExternalCursor == null) {
                    setTitle(R.string.sdcard_error_title);
                    TextView tv = (TextView) this.findViewById(R.id.sd_message);
                    tv.setText(R.string.sdcard_error_message);
                }
                if (mExternalCursor.getCount() > 0) {
                    setTitle(R.string.videos_title);
                } else {
                    setTitle(R.string.no_videos_title);
                }
                initAdapter();
			}
		}
    }

    private void displayDatabaseError(boolean isInitFinished) {
        if (isFinishing()) {
            return;
        }

        String status = Environment.getExternalStorageState();
        int title, message;

        if (!isInitFinished) {
            if (mInternalCursor == null) {
                title = R.string.system_initing_title;
                message = R.string.system_initing_message;
            } else if (mExternalCursor == null) {
                title = R.string.scanning_system_title;
                message = R.string.scanning_system_message;
            } else {
                title = R.string.scanning_sdcard_title;
                message = R.string.scanning_sdcard_message;
            }
        } else {
            if (android.os.Environment.isExternalStorageRemovable()) {
                title = R.string.sdcard_error_title;
                message = R.string.sdcard_error_message;
            } else {
                title = R.string.sdcard_error_title_nosdcard;
                message = R.string.sdcard_error_message_nosdcard;
            }
        }

        if (status.equals(Environment.MEDIA_SHARED) ||
                status.equals(Environment.MEDIA_UNMOUNTED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                title = R.string.sdcard_busy_title;
                message = R.string.sdcard_busy_message;
            } else {
                title = R.string.sdcard_busy_title_nosdcard;
                message = R.string.sdcard_busy_message_nosdcard;
            }
        } else if (status.equals(Environment.MEDIA_REMOVED)) {
            if (android.os.Environment.isExternalStorageRemovable()) {
                title = R.string.sdcard_missing_title;
                message = R.string.sdcard_missing_message;
            } else {
                title = R.string.sdcard_missing_title_nosdcard;
                message = R.string.sdcard_missing_message_nosdcard;
            }
        } else if (status.equals(Environment.MEDIA_MOUNTED)){
            setTitle("");
            Intent intent = new Intent();
            intent.setClass(this, ScanningProgress.class);
            startActivity(intent);
        } else if (!TextUtils.equals(mLastSdStatus, status)) {
            mLastSdStatus = status;
        }

        setTitle(title);
        View v = findViewById(R.id.sd_message);
        if (v != null) {
            v.setVisibility(View.VISIBLE);
        }
        v = findViewById(R.id.sd_icon);
        if (v != null) {
            v.setVisibility(View.VISIBLE);
        }
        v = findViewById(android.R.id.list);
        if (v != null) {
            v.setVisibility(View.GONE);
        }
        v = findViewById(R.id.buttonbar);
        if (v != null) {
            v.setVisibility(View.GONE);
        }
        TextView tv = (TextView)findViewById(R.id.sd_message);
        tv.setText(message);
    }

    public BroadcastReceiver mScanListener = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            Uri uri = intent.getData();
            onCheckAction(action, uri);
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Intent intent = getIntent();

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mFilterString = intent.getStringExtra(SearchManager.QUERY);
        }

        init();
    }

    public void init() {

        // Set the layout for this activity.  You can find it
        // in assets/res/any/layout/media_picker_activity.xml
        setContentView(R.layout.media_picker_activity);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        f.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        f.addDataScheme("file");
        registerReceiver(mScanListener, f);

        MakeCursor();

        if (mExternalCursor == null) {
            displayDatabaseError(false);
            return;
        }

        if (mExternalCursor.getCount() > 0) {
            setTitle(R.string.videos_title);
        } else {
            setTitle(R.string.no_videos_title);
        }

        //Don't show tab bar in video list.
        TabWidget tw = (TabWidget)findViewById(R.id.buttonbar);
        tw.setVisibility(View.GONE);

        // Map Cursor columns to views defined in media_list_item.xml
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_1,
                mExternalCursor,
                new String[] { MediaStore.Video.Media.DISPLAY_NAME},
                new int[] { android.R.id.text1 });

        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        mExternalCursor.moveToPosition(position);
        String type = mExternalCursor.getString(mExternalCursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE));
        intent.setDataAndType(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id), type);
        
        startActivity(intent);
    }

    private void MakeCursor() {
        String[] cols = new String[] {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.ARTIST
        };
        ContentResolver resolver = getContentResolver();
        if (resolver == null) {
            System.out.println("resolver = null");
        } else {
            mSortOrder = MediaStore.Video.Media.DISPLAY_NAME + " COLLATE UNICODE";
            mSortOrder = MediaStore.Video.Media.DISPLAY_NAME + " COLLATE UNICODE";
            if (TextUtils.isEmpty(mFilterString)){
                mWhereClause = MediaStore.Video.Media.DISPLAY_NAME + " != ''";
            }else{
                mWhereClause = MediaStore.Video.Media.DISPLAY_NAME + " like '%"+mFilterString+"%'";
            }
            mExternalCursor = resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cols, mWhereClause , null, mSortOrder);
            mInternalCursor = resolver.query(MediaStore.Video.Media.INTERNAL_CONTENT_URI,
                cols, mWhereClause , null, mSortOrder);
        }
    }

    @Override
    public void onDestroy() {
        if (mExternalCursor != null) {
            mExternalCursor.close();
        }

        if (mInternalCursor != null) {
            mInternalCursor.close();
        }

        unregisterReceiver(mScanListener);
        super.onDestroy();
    }

    private int mScanErrorCounter;
    private static String mLastSdStatus;
    private Cursor mExternalCursor;
    private Cursor mInternalCursor;
    private String mWhereClause;
    private String mSortOrder;
}

