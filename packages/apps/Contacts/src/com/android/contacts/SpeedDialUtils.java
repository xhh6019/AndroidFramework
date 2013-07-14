/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
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

package com.android.contacts;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;

/**
 * this class is used to set or get speed number in preference.
 * @author c_hluo
 *
 */
public class SpeedDialUtils {

    public static final int NUM_TWO = 0;
    public static final int NUM_THREE = 1;
    public static final int NUM_FOUR = 2;
    public static final int NUM_FIVE = 3;
    public static final int NUM_SIX = 4;
    public static final int NUM_SEVEN = 5;
    public static final int NUM_EIGHT = 6;
    public static final int NUM_NINE = 7;

    public static final int INFO_NUMBER = 0;
    public static final int INFO_NAME = 1;

    private static final String[] numKeys = new String[] {"num2_key","num3_key","num4_key","num5_key",
                                                          "num6_key","num7_key","num8_key","num9_key"};
    private SharedPreferences mPref;

    private Context mContext;

    /*
     * constructed function, in fact used to init shared preferences object.
     */
    public SpeedDialUtils(Context context) {
        mContext = context;
        mPref = mContext.getApplicationContext().getSharedPreferences("speedDial_Num", context.MODE_PRIVATE);
    }

    /*
     * set speed number to share preference
     */
    public void storeRawContactId(int numId, int keyValue) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putInt(numKeys[numId], keyValue);
        editor.commit();
    }

    /*
     * get raw contact id from share preference
     */
    public int getRawContactId(int numId) {
        return mPref.getInt(numKeys[numId], 0);
    }

    /*
     * get speed dial information(name or number) according number key
     */
    public String getSpeedDialInfo(int rawContactId, int infoType) {
        Cursor c = null;
        String speedDialInfo = null;

        if (rawContactId == 0) {
            return null;
        }

        Uri lookupUri = null;
        String querySelection = null;
        String tableColumn = null;
        if (infoType == INFO_NUMBER) {
            lookupUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            querySelection = Data.RAW_CONTACT_ID+"="+rawContactId;
            tableColumn = ContactsContract.CommonDataKinds.Phone.NUMBER;
        } else {
            //get contact name
            lookupUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
            querySelection = RawContacts.DELETED+"="+0;
            tableColumn = RawContacts.DISPLAY_NAME_PRIMARY;
       }

        try{
            c = mContext.getContentResolver().query(lookupUri, null, querySelection, null, null);
            if( c != null && c.moveToFirst() ) {
                speedDialInfo = c.getString(c.getColumnIndexOrThrow(tableColumn));
            }
         }catch(Exception e){
             //exception happen
         } finally {
             if (c != null)
                 c.close();
         }

        return speedDialInfo;
    }
}
