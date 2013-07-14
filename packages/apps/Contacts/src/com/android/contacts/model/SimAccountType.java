/*
  * Copyright (C) 2012, Code Aurora Forum. All rights reserved.
 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
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

package com.android.contacts.model;

import com.android.contacts.R;
import com.android.contacts.model.AccountType.DefinitionException;
import com.google.android.collect.Lists;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.view.inputmethod.EditorInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.telephony.MSimTelephonyManager;

public class SimAccountType extends BaseAccountType{
    private static final String TAG = "SimContactsType";

    public static final String ACCOUNT_TYPE = "com.android.sim";
    public static final String RES_PACKAGE_NAME = "com.qrd.simcontacts";
    public static final int FLAGS_PERSON_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;
    public static final int FLAGS_PHONETIC = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_PHONETIC;
    protected static final int FLAGS_PHONE = EditorInfo.TYPE_CLASS_PHONE;
    private static Context mContext;

    public SimAccountType(Context context, String resPackageName) {
        this.accountType = ACCOUNT_TYPE;
        this.resPackageName = RES_PACKAGE_NAME;
        this.summaryResPackageName = RES_PACKAGE_NAME;
        this.titleRes = R.string.account_sim;
        this.iconRes = R.drawable.ic_launcher_contacts;

        this.mContext = context;

        try {
            addDataKindStructuredName(context);
            addDataKindDisplayName(context);
            addDataKindPhone(context);
            addDataKindEmail(context);
            mIsInitialized = true;
        } catch (DefinitionException e) {
            Log.e(TAG, "Problem building account type", e);
        }
    }

    @Override
    protected DataKind addDataKindStructuredName(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindStructuredName(context);
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.DISPLAY_NAME, R.string.name_for_sim,
                FLAGS_PERSON_NAME));

        return kind;
    }


    @Override
    protected DataKind addDataKindPhone(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindPhone(context);

        kind.iconAltRes = 0x7f020003;
        kind.typeOverallMax = 2;
        kind.isList = true;
        kind.typeColumn = Phone.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
	 // Modify By Michael.Chan   Apr.10 2013   Number dispear HOME_TYPE in SimAccountType
        //kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));// This is used to save ANR records
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

        return kind;
    }

    @Override
    protected DataKind addDataKindEmail(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindEmail(context);

        kind.typeOverallMax = 1;
        kind.typeColumn = Email.TYPE;
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Email.ADDRESS, R.string.emailLabelsGroup, FLAGS_EMAIL));
        return kind;
    }


    @Override
    public boolean isGroupMembershipEditable() {
        return true;
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }

}
