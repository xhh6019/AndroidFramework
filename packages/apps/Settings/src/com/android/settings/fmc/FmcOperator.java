/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
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

package com.android.settings.fmc;

import android.content.Context;
import android.content.Intent;
import android.net.FmcNotifier;
import android.net.FmcProvider;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;


public class FmcOperator{
    static final String TAG = "FmcOperator";
    static final String FMC_STATE_CHANGED_ACTION = "android.fmc.FMC_STATE_CHANGED_ACTION";

    public static final int START_FMC_EVENT = 1;
    public static final int START_TIMEOUT_EVENT = 2;   // time out 120s
    public static final int STOP_FMC_EVENT = 3;
    private static final int MAX_RETRY_COUNT = 3;

    public static final long START_TIMEOUT = 120*1000L;
    public static final long STOP_TIMEOUT = 10*1000L;

    private int mRetryCount = MAX_RETRY_COUNT;
    private static FmcOperator mInstance = null;
    private FmcProvider mFmcProvider = null;
    private static Context mContext = null;

    private FmcOperator() {}

    public static FmcOperator getInstance(Context context) {

        if (mInstance == null) {
            mInstance = new FmcOperator();
        }
        mContext = context;
        return mInstance;
    }

    private FmcProvider getFmcProvider() {
        if (mFmcProvider == null) {
            try {
                mFmcProvider = new FmcProvider(fmcNotifier);
            } catch (Exception e  ) {
                Log.e(TAG, "getFmcProvider error:"+e.toString());
                return null;
            }
        }
        return mFmcProvider;
    }

    FmcNotifier fmcNotifier = new FmcNotifier(){
         public void onFmcStatus(int status) {
             Log.d(TAG, "FmcNotifier receive msg, state=" + status);

             switch (status) {
                 case FmcNotifier.FMC_STATUS_ENABLED:
                 case FmcNotifier.FMC_STATUS_REGISTRATION_SUCCESS:
                     myHandler.removeCallbacks(taskStartTimeout);
                     myHandler.removeCallbacks(taskStopTimeout);
                     break;

                 case FmcNotifier.FMC_STATUS_DS_NOT_AVAIL:
                 case FmcNotifier.FMC_STATUS_NOT_AVAIL:
                 case FmcNotifier.FMC_STATUS_FAILURE:
                 case FmcNotifier.FMC_STATUS_SHUTTING_DOWN:
                 case FmcNotifier.FMC_STATUS_CLOSED:
                     myHandler.removeCallbacks(taskStartTimeout);
                     myHandler.removeCallbacks(taskStopTimeout);
                     break;
                 default:
                     break;
             }

             // save FMC status in setting provider, in case other
             // app may need to query this info
             android.provider.Settings.System.putInt(mContext.getContentResolver(),
                     Settings.System.FMC_STATUS, status);

             // also send broadcast to notify status change
             Intent intent = new Intent(FMC_STATE_CHANGED_ACTION);
             intent.putExtra("FmcStatus", status);
             mContext.sendBroadcast(intent);
         }
    };


    private Handler myHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case START_FMC_EVENT:
                handleStartFmc();
                break;

            case START_TIMEOUT_EVENT:
                handleStartTimeOut();
                break;

            case STOP_FMC_EVENT:
                handleStopFmc();
                break;
            }
        }
    };

    Runnable taskStartTimeout = new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            myHandler.obtainMessage(START_TIMEOUT_EVENT).sendToTarget();
        }

    };

    Runnable taskStopTimeout = new Runnable() {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            myHandler.obtainMessage(START_FMC_EVENT).sendToTarget();
        }

    };

    // send the start FMC event immediately
    public void startFmc() {
       Log.d(TAG,"startFmc");
       myHandler.obtainMessage(START_FMC_EVENT).sendToTarget();
//       myHandler.postDelayed(taskStartTimeout, START_TIMEOUT);
    }

    public void stopFmc() {
        Log.d(TAG,"stopFmc");
        myHandler.obtainMessage(STOP_FMC_EVENT).sendToTarget();
    }

    // internal use method
    private void handleStartFmc() {
        Log.d(TAG,"handleStartFmc");
        boolean result = false;
        FmcProvider fmcProvider = getFmcProvider();

        if (fmcProvider != null) {
            try {
                result = fmcProvider.startFmc();
                Log.d(TAG,"handleStartFmc: ret = " + result);
            } catch (Exception e  ) {
                Log.e(TAG,"handleStartFmc: exception while startFmc.");
            }
        }
    }

    private void handleStartTimeOut() {

        myHandler.obtainMessage(STOP_FMC_EVENT).sendToTarget();
        mRetryCount--;
        Log.d(TAG,"handleStartTimeOut retry count="+mRetryCount);
        if (mRetryCount > 0) {
            myHandler.postDelayed(taskStopTimeout, STOP_TIMEOUT);
        } else {
            // reset counter, give up to enable fmc
            mRetryCount = MAX_RETRY_COUNT;
        }
    }

    private void handleStopFmc() {
        boolean ret = false;
        FmcProvider fmcProvider = getFmcProvider();
        Log.d(TAG,"handleStopFmc");
        if (fmcProvider != null) {
            try {
                ret = fmcProvider.stopFmc();
                Log.d(TAG,"handleStopFmc: ret = " + ret);
            } catch (Exception e  ) {
                Log.e(TAG,"handleStopFmc: exception while stopFmc.");
            }
        }
    }
}
