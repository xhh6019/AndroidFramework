/*
 * // Copyright 2011 Google Inc.
 * // All Rights Reserved.
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.mms;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

import java.text.SimpleDateFormat;
import java.util.Date;
import android.os.Environment;
/**
 * The TempFileProvider manages a uri, backed by a file, for passing to the camera app for
 * capturing pictures and videos and storing the data in a file in the messaging app.
 */
public class TempFileProvider extends ContentProvider {
    private static String TAG = "TempFileProvider";
    private static boolean DEBUG = true;
    private String title;

    /**
     * The content:// style URL for this table
     */
    public static final Uri SCRAP_CONTENT_URI = Uri.parse("content://mms_temp_file/scrapSpace");

    private static final int MMS_SCRAP_SPACE = 1;
    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sURLMatcher.addURI("mms_temp_file", "scrapSpace", MMS_SCRAP_SPACE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        return 0;
    }

    private ParcelFileDescriptor getTempStoreFd(String mode) {
        String fileName = getScrapPath(getContext());
        ParcelFileDescriptor pfd = null;

        try {
            File file = new File(fileName);

            // make sure the path is valid and directories created for this file.
            File parentFile = file.getParentFile();
            if (!parentFile.exists() && !parentFile.mkdirs()) {
                Log.e(TAG, "[TempFileProvider] tempStoreFd: " + parentFile.getPath() +
                        "does not exist!");
                return null;
            }

            if ("rw".equals(mode)) {
                pfd = ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_READ_WRITE
                                | ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE);
            }else {
                pfd = ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_READ_WRITE
                                | ParcelFileDescriptor.MODE_CREATE);
            }

        } catch (Exception ex) {
            Log.e(TAG, "getTempStoreFd: error creating pfd for " + fileName, ex);
        }

        return pfd;
    }

    @Override
    public String getType(Uri uri) {
        return "*/*";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // if the url is "content://mms/takePictureTempStore", then it means the requester
        // wants a file descriptor to write image data to.

        ParcelFileDescriptor fd = null;
        int match = sURLMatcher.match(uri);

        if (DEBUG) {
            Log.d(TAG, "openFile: uri=" + uri + ", mode=" + mode);
        }

        switch (match) {
            case MMS_SCRAP_SPACE:
                fd = getTempStoreFd(mode);
                break;
        }

        return fd;
    }


    /**
     * This is the scrap file we use to store the media attachment when the user
     * chooses to capture a photo to be attached . We pass {#link@Uri} to the Camera app,
     * which streams the captured image to the uri. Internally we write the media content
     * to this file. It's named '.temp.jpg' so Gallery won't pick it up.
     */
    public static String getScrapPath(Context context, String fileName) {
    	//change to /mnt/emmc/@CHENHUO20130227
	//	return "/mnt/emmc/"+"/"+fileName;
          if (android.os.Environment.getExternalStorageState().equals(  
        	    android.os.Environment.MEDIA_MOUNTED)) {  
                String storage = creatfile();
                return storage+ '/' +fileName;
          }else{
                return "/mnt/emmc/"+"/"+fileName;
          }
		// return context.getExternalCacheDir().getAbsolutePath() + "/" + fileName;
    }

    public static String getScrapPath(Context context) {
        return getScrapPath(context, ".temp.jpg");
    }

    /**
     * renameScrapFile renames the single scrap file to a new name so newer uses of the scrap
     * file won't overwrite the previously captured data.
     * @param fileExtension file extension for the temp file, typically ".jpg" or ".3gp"
     * @param uniqueIdentifier a separator to add to the file to make it unique,
     *        such as the slide number. This parameter can be empty or null.
     * @return uri of renamed file. If there's an error renaming, null will be returned
     */
    public static Uri renameScrapFile(String fileExtension, String uniqueIdentifier,
            Context context) {
        String filePath = getScrapPath(context);
        // There's only a single scrap file, but there can be several slides. We rename
        // the scrap file to a new scrap file with the slide number as part of the filename.

        // Replace the filename ".temp.jpg" with ".temp#.[jpg | 3gp]" where # is the unique
        // identifier. The content of the file may be a picture or a .3gp video.
        if (uniqueIdentifier == null) {
            uniqueIdentifier = "";
        }     
        long dateTaken = System.currentTimeMillis();
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat( "'VID'_yyyyMMdd_HHmm");
        String title= dateFormat.format(date);
        File newTempFile = new File(getScrapPath(context, title + uniqueIdentifier +
        //File newTempFile = new File(getScrapPath(context, ".temp" + uniqueIdentifier +
                fileExtension));
        File oldTempFile = new File(filePath);
        // remove any existing file before rename
        boolean deleted = newTempFile.delete();
        if (!oldTempFile.renameTo(newTempFile)) {
            return null;
        }
        return Uri.fromFile(newTempFile);
    }

    public static String creatfile(){
        String DCIM = Environment.getExternalStorageDirectory().toString() + "/DCIM";
        File dir = new File(DCIM);
        if (!dir.exists())
	{
	    dir.mkdirs();
	}
	String DIRECTORY = DCIM + "/Camera";

	File dir1 = new File(DIRECTORY);
	if (!dir1.exists())
	{
	   dir1.mkdirs();
	}
        return DIRECTORY;
    }
}
