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

package com.android.phone;

import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.database.Cursor;
import android.provider.ContactsContract;


import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.MSimPhoneFactory;
import com.android.internal.telephony.Phone;

public class MSimDialerActivity extends Activity {
    private static final String TAG = "MSimDialerActivity";
    private static final boolean DBG = true;

    private Context mContext;
    private String mCallNumber;
    private String mNumber;
    private AlertDialog mAlertDialog = null;
    private TextView mTextNumber;
    private CountDownTimer mCountDownTimer;
    private TextView mCountDownTimerText;
    private Intent mIntent;
    private int mPhoneCount = 0;
    private int mTimer;

    public static final int INVALID_SUB = 99;

    private static final int INDEX_MULTI_SIM_DIALOG = 1;

    private static final long INTERVAL_COUNTDOWN = 500;
    private static final int WHAT_UPDATE_LEFT_TIME = 0;
    private long currentLeftTime;
    private boolean isStarted = false;

    private View multiDialerlayout;

    private Handler updateHanler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_UPDATE_LEFT_TIME:
                    currentLeftTime -= INTERVAL_COUNTDOWN;
                    Log.d(TAG, "update handler left time :" + currentLeftTime);
                    if (currentLeftTime <= 0) {
                        startOutgoingCall(getVoiceSubscription());
                    } else
                        sendEmptyMessageDelayed(WHAT_UPDATE_LEFT_TIME, INTERVAL_COUNTDOWN);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        currentLeftTime = -1;
        mContext = getApplicationContext();
        mCallNumber = getResources().getString(R.string.call_number);	 
    }

    private boolean isCallbackPriorityEnabled() {
        int enabled;
        try {
            enabled = Settings.System.getInt(getContentResolver(),
                Settings.System.CALLBACK_PRIORITY_ENABLED);
        } catch (SettingNotFoundException snfe) {
            enabled = 1;
        }
        return (enabled == 1);
    }

    private int getVoiceSubscription() {
        int voiceSub = MSimPhoneFactory.getVoiceSubscription();

        if (isCallbackPriorityEnabled()) {
            voiceSub = mIntent.getIntExtra(SUBSCRIPTION_KEY, voiceSub);
            Log.i(TAG, "Preferred callback enabled");
            if (DBG) Log.v(TAG, "getVoiceSubscription return:" + mIntent.getExtra(SUBSCRIPTION_KEY));
        }
        return voiceSub;
    }

    @Override
    protected void onResume() {
        super.onResume();

        stopUpdateLeftTime();
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mIntent = getIntent();
        if (DBG) Log.v(TAG, "Intent = " + mIntent);

        mNumber = PhoneNumberUtils.getNumberFromIntent(mIntent, this);
        if (DBG) Log.v(TAG, "mNumber " + mNumber);
        if (mNumber != null) {
            mNumber = PhoneNumberUtils.convertKeypadLettersToDigits(mNumber);
            mNumber = PhoneNumberUtils.stripSeparators(mNumber);
        }

        Phone phone = null;
        boolean phoneInCall = false;
        //checking if any of the phones are in use
        for (int i = 0; i < mPhoneCount; i++) {
             phone = MSimPhoneFactory.getPhone(i);
             boolean inCall = isInCall(phone);
             if ((phone != null) && (inCall)) {
                 phoneInCall = true;
                 break;
             }
        }
        mTimer = getCountdownTimer();
        if (phoneInCall) {
            if (DBG) Log.v(TAG, "subs [" + phone.getSubscription() + "] is in call");
            // use the sub which is already in call
            startOutgoingCall(phone.getSubscription());
        } else {
            if (DBG) Log.v(TAG, "launch dsdsdialer");
            // if none in use, launch the MultiSimDialer
            if(mTimer == 0){
                startOutgoingCall(getVoiceSubscription());
            }else{
                launchMSDialer();
            }
        }
        Log.d(TAG, "end of onResume()");
    }

    protected void onPause() {
        super.onPause();
        closeMultiSimDialer();
        if (!isStarted)
            startUpdateLeftTime();
    }

    private int getSubscriptionForEmergencyCall(){
       Log.d(TAG,"emergency call, getVoiceSubscriptionInService");
       int sub = PhoneApp.getInstance().getVoiceSubscriptionInService();
       return sub;
    }

   private String getMultiSimName(int subscription) {
       return Settings.System.getString(mContext.getContentResolver(),
               Settings.System.MULTI_SIM_NAME[subscription]);
   }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case INDEX_MULTI_SIM_DIALOG:
                LayoutInflater inflater = (LayoutInflater) mContext.
                        getSystemService(LAYOUT_INFLATER_SERVICE);
                multiDialerlayout = inflater.inflate(R.layout.dialer_ms,
                        (ViewGroup) findViewById(R.id.layout_root));

                AlertDialog.Builder builder = new AlertDialog.Builder(MSimDialerActivity.this);
                builder.setView(multiDialerlayout).setCancelable(false);
                builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        Log.d(TAG, "key code is :" + keyCode);
                        switch (keyCode) {
                            case KeyEvent.KEYCODE_BACK: {
                                startOutgoingCall(INVALID_SUB);
                                return true;
                            }
                            case KeyEvent.KEYCODE_CALL: {
                                Log.d(TAG, "event is" + event.getAction());
                                if (event.getAction() == KeyEvent.ACTION_UP) {
                                    return true;
                                } else {
                                    startOutgoingCall(getVoiceSubscription());
                                    return true;
                                }
                            }
                            case KeyEvent.KEYCODE_SEARCH:
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                return builder.create();
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case INDEX_MULTI_SIM_DIALOG:
                mTextNumber = (TextView) multiDialerlayout.findViewById(R.id.CallNumber);

		  
                String vm = "";
                if (mIntent.getData() != null)
                    vm = mIntent.getData().getScheme();

                if ((vm != null) && (vm.equals("voicemail"))) {
                    mTextNumber.setText(mCallNumber + "VoiceMail");			
                    Log.d(TAG, "its voicemail!!!");
                } else {
                    mTextNumber.setText(mCallNumber + getPeople(mNumber));
                }

                Button callCancel = (Button) multiDialerlayout.findViewById(R.id.callcancel);
                callCancel.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        startOutgoingCall(INVALID_SUB);
                    }
                });

                Button[] callButton = new Button[mPhoneCount];
                int[] callMark = {
                        R.id.callmark1, R.id.callmark2
                };
                // int[] subString = {R.string.sub_1, R.string.sub_2};
                int index = 0;
                for (index = 0; index < mPhoneCount; index++) {
                    callButton[index] = (Button) multiDialerlayout.findViewById(callMark[index]);
                    callButton[index].setText(getMultiSimName(index));
                    callButton[index].setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            switch (v.getId()) {
                                case R.id.callmark1:
                                    startOutgoingCall(MSimConstants.SUB1);
                                    break;
                                case R.id.callmark2:
                                    startOutgoingCall(MSimConstants.SUB2);
                                    break;
                            }
                        }
                    });
                }

                mCountDownTimerText = (TextView) multiDialerlayout
                        .findViewById(R.id.CountDownTimer);
                if (isAddTimer()) {
                    mCountDownTimer = new CountDownTimer(currentLeftTime > -1 ? currentLeftTime
                            : mTimer * 1000, 500) {
                        public void onTick(long millisUntilFinished) {
                            currentLeftTime = millisUntilFinished;
                            Log.d(TAG, "count down left time :" + currentLeftTime);
                            if (1 == (millisUntilFinished / 500) % 2) {
                                mCountDownTimerText.setText(getResources().getString(
                                        R.string.count_down_timer)
                                        + (millisUntilFinished / 1000 + 1));
                            }
                        }

                        public void onFinish() {
                            startOutgoingCall(getVoiceSubscription());
                        }
                    };
                    mCountDownTimer.start();
                } else {
                    mCountDownTimerText.setVisibility(View.GONE);
                    mCountDownTimer = null;
                }

                if (MSimConstants.SUB1 == getVoiceSubscription()) {
                    callButton[MSimConstants.SUB1]
                            .setBackgroundResource(R.drawable.highlight_btn_call);
                } else {
                    callButton[MSimConstants.SUB2]
                            .setBackgroundResource(R.drawable.highlight_btn_call);
                }
                break;
        }
    }

    private void launchMSDialer() {
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(mNumber);
        if (isEmergency) {
            Log.d(TAG,"emergency call");
            startOutgoingCall(getSubscriptionForEmergencyCall());
            return;
        }
        showDialog(INDEX_MULTI_SIM_DIALOG);
    }

    private void stopUpdateLeftTime(){
        updateHanler.removeMessages(WHAT_UPDATE_LEFT_TIME);
    }

    private void startUpdateLeftTime(){
        if (currentLeftTime > 0 && !updateHanler.hasMessages(WHAT_UPDATE_LEFT_TIME))
            updateHanler.sendEmptyMessageDelayed(WHAT_UPDATE_LEFT_TIME, INTERVAL_COUNTDOWN);
    }

    private boolean isAddTimer() {
        if (mTimer == -1) {
            return false;
        } else {
            return true;
        }
    }

    protected void closeMultiSimDialer() {
        stopUpdateLeftTime();
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
    }

    private int getCountdownTimer() {
        int timer = -1;
        try {
            timer = Settings.System.getInt(getContentResolver(),
                Settings.System.MULTI_SIM_COUNTDOWN);
        } catch (SettingNotFoundException snfe) {
            Log.d(TAG, Settings.System.MULTI_SIM_COUNTDOWN + " setting does not exist");
        }
        return timer;
    }

    boolean isInCall(Phone phone) {
        if (phone != null) {
            if ((phone.getForegroundCall().getState().isAlive()) ||
                   (phone.getBackgroundCall().getState().isAlive()) ||
                   (phone.getRingingCall().getState().isAlive()))
                return true;
        }
        return false;
    }

    private void startOutgoingCall(int subscription) {
        isStarted = true;
        closeMultiSimDialer();
        mIntent.putExtra(SUBSCRIPTION_KEY, subscription);
        mIntent.setClass(MSimDialerActivity.this, OutgoingCallBroadcaster.class);
        if (DBG)
            Log.v(TAG, "startOutgoingCall for sub " + subscription + " from intent: " + mIntent);
        if (subscription < mPhoneCount) {
            setResult(RESULT_OK, mIntent);
        } else {
            setResult(RESULT_CANCELED, mIntent);
            Log.d(TAG, "call cancelled");
        }
        finish();
    }

private String getPeople(String sNumber) {  
	String[] projection = { ContactsContract.PhoneLookup.DISPLAY_NAME,	
							ContactsContract.CommonDataKinds.Phone.NUMBER};  
	String name = "" ;
	Cursor cursor = this.getContentResolver().query(  
			ContactsContract.CommonDataKinds.Phone.CONTENT_URI,  
			projection,    // Which columns to return.	 
			ContactsContract.CommonDataKinds.Phone.NUMBER + " = '" + sNumber + "'", // WHERE clause.   
			null,		   // WHERE clause value substitution	
			null);	 // Sort order.   

	if( cursor == null ) {	
		return sNumber;  
	}  
	for( int i = 0; i < cursor.getCount(); i++ )  
	{  
		cursor.moveToPosition(i);  
		int nameFieldColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);	 
		name = cursor.getString(nameFieldColumnIndex);  
	}  
	cursor.close()  ;
	if(name == ""){
		name = sNumber  ;
	}
	return name  ;
} 


   
}
