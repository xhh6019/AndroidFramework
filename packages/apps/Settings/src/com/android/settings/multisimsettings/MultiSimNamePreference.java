/*
 *
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
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

package com.android.settings.multisimsettings;

import android.app.AlertDialog;
import android.app.Dialog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputFilter.LengthFilter;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.EditText;
import android.telephony.TelephonyManager;

import android.util.Log;

import com.android.settings.R;


public class MultiSimNamePreference extends EditTextPreference implements TextWatcher, Preference.OnPreferenceChangeListener {
    private static final String TAG = "ChannelNamePreference";

    private static final int CHANNEL_NAME_MAX_LENGTH = 6;

    private LocalMultiSimSettingsManager mLocalManager;

    private int mSubscription;
    private String mMultiSimName;

    public MultiSimNamePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLocalManager = LocalMultiSimSettingsManager.getInstance(context);

    }

    public void setSubscription(int subscription) {
        mSubscription = subscription;
        mMultiSimName = mLocalManager.getMultiSimName(subscription);
        setSummary(mMultiSimName);
    }

    public void resume() {
		//set the title/summary with slot@CHENHUO20130228
		if (TelephonyManager.getDefault().isMultiSimEnabled()){
			if (mSubscription ==1){
				setTitle(R.string.title_sim_alias);
			}else{
				setTitle(R.string.title_sim_uim_alias);
			}
		}
		//END
        setOnPreferenceChangeListener(this);
        // Make sure the OK button is disabled (if necessary) after rotation
        EditText et = getEditText();
        if (et != null) {
            et.addTextChangedListener(this);
            Dialog d = getDialog();
            if (d instanceof AlertDialog) {
                Button b = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
                b.setEnabled(et.getText().length() > 0);
            }
        }
        setSummary(mMultiSimName);
    }

    public void pause() {
        EditText et = getEditText();
        if (et != null) {
            et.removeTextChangedListener(this);
        }
        setOnPreferenceChangeListener(null);
    }


    @Override
    protected void onClick() {
        super.onClick();

        // The dialog should be created by now
        EditText et = getEditText();
        if (et != null) {
            et.setText(mLocalManager.getMultiSimName(mSubscription));
        }
    }

    public boolean onPreferenceChange(Preference preference, Object value) {
        Log.i("sim name setting", "onPreferenceChange " + value);

        mMultiSimName = (String)value;
        mLocalManager.setMultiSimName(mMultiSimName, mSubscription);
        setSummary(mMultiSimName);

        return true;
    }

    // TextWatcher interface
    public void afterTextChanged(Editable s) {
        limitTextSize(s.toString().trim());
        Dialog d = getDialog();
        if (d instanceof AlertDialog) {
            ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(s.length() > 0);
        }
    }

    // TextWatcher interface
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // not used
    }

    // TextWatcher interface
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // not used
    }

    private void limitTextSize(String s) {
        EditText et = getEditText();
        int len = 0;

        if (et != null) {
            for (int i = 0; i < s.length(); i++) {
                int ch = Character.codePointAt(s, i);

                if (ch >= 0x00 && ch <= 0xFF)
                    len++;
                else
                    len += 2;
                if (len > CHANNEL_NAME_MAX_LENGTH) {
                    s = s.substring(0, i);
                    et.setText(s);
                    et.setSelection(s.length(), s.length());
                    break;
                }
            }
        }
    }
}
