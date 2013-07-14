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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.provider.LocalGroups;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import com.android.contacts.R;

public class AddLocalGroupDialog extends AlertDialog implements OnClickListener, TextWatcher {

    public static interface AddGroupListener {
        void onAddGroup(String name);
    }

    public static final int GROUP_NAME_MAX_LENGTH = 20;

    private EditText groupSettings;

    private AddGroupListener addGroupListener;

    public AddLocalGroupDialog(Context context, AddGroupListener addGroupListener) {
        super(context);
        this.addGroupListener = addGroupListener;
        groupSettings = new EditText(context);
        groupSettings.setHint(R.string.title_group_name);
        groupSettings.addTextChangedListener(this);
        setTitle(R.string.title_group_add);
        setView(groupSettings);
        setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(android.R.string.cancel),
                this);
        setButton(DialogInterface.BUTTON_POSITIVE, context.getString(android.R.string.ok), this);
        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(
                        groupSettings.getText().length() > 0);
            }
        });
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                String name = groupSettings.getText().toString();
                if(checkGroupTitleExist(name)){
                    Toast.makeText(getContext(), R.string.error_group_exist, Toast.LENGTH_SHORT).show();
                }else{
                    addGroupListener.onAddGroup(name);
                }
                break;
        }
        groupSettings.setText(null);
    }

    private boolean checkGroupTitleExist(String name) {
        Cursor c = null;
        try {
            c = this.getContext().getContentResolver()
                    .query(LocalGroups.CONTENT_URI, null, LocalGroups.GroupColumns.TITLE + "=?",
                            new String[] {
                                name
                            },
                            null);
            if (c != null)
                return c.getCount() > 0;
            else {
                return false;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // TODO Auto-generated method stub

    }

    @Override
    public void afterTextChanged(Editable s) {
        limitTextSize(s.toString());
        getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(groupSettings.getText().length() > 0);
    }

    private void limitTextSize(String s) {
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            int ch = Character.codePointAt(s, i);
            // to make sure no matter the language is English or Chinese the
            // group name is displayed in single line
            if (ch >= 0x00 && ch <= 0xFF)
                len++;
            else
                len += 2;
            if (len > GROUP_NAME_MAX_LENGTH || ch == 10 || ch == 32) {
                s = s.substring(0, i);
                groupSettings.setText(s);
                groupSettings.setSelection(s.length(), s.length());
                break;
            }
        }
    }

}
