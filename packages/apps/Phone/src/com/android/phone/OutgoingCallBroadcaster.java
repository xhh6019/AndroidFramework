/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2010-2012, Code Aurora Forum. All rights reserved.
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SubscriptionManager;
import com.android.internal.telephony.MSimPhoneFactory;
import android.telephony.TelephonyManager;
import android.util.Config;

import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;

/**
 * OutgoingCallBroadcaster receives CALL and CALL_PRIVILEGED Intents, and
 * broadcasts the ACTION_NEW_OUTGOING_CALL intent which allows other
 * applications to monitor, redirect, or prevent the outgoing call.

 * After the other applications have had a chance to see the
 * ACTION_NEW_OUTGOING_CALL intent, it finally reaches the
 * {@link OutgoingCallReceiver}, which passes the (possibly modified)
 * intent on to the {@link SipCallOptionHandler}, which will
 * ultimately start the call using the CallController.placeCall() API.
 *
 * Emergency calls and calls where no number is present (like for a CDMA
 * "empty flash" or a nonexistent voicemail number) are exempt from being
 * broadcast.
 */
public class OutgoingCallBroadcaster extends Activity
        implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

    private static final String PERMISSION = android.Manifest.permission.PROCESS_OUTGOING_CALLS;
    private static final String TAG = "OutgoingCallBroadcaster";
    private static final boolean DBG =
            (PhoneApp.DBG_LEVEL >= 1) && (SystemProperties.getInt("ro.debuggable", 0) == 1);
    // Do not check in with VDBG = true, since that may write PII to the system log.
    private static final boolean VDBG = false;
    private int mSubscription;
    public static final String ACTION_SIP_SELECT_PHONE = "com.android.phone.SIP_SELECT_PHONE";
    public static final String EXTRA_ALREADY_CALLED = "android.phone.extra.ALREADY_CALLED";
    public static final String EXTRA_ORIGINAL_URI = "android.phone.extra.ORIGINAL_URI";
    public static final String EXTRA_NEW_CALL_INTENT = "android.phone.extra.NEW_CALL_INTENT";
    public static final String EXTRA_SIP_PHONE_URI = "android.phone.extra.SIP_PHONE_URI";
    public static final String EXTRA_ACTUAL_NUMBER_TO_DIAL =
            "android.phone.extra.ACTUAL_NUMBER_TO_DIAL";

    /** the key used to specify subscription to be used for emergency calls */
    public static final String BLUETOOTH = "Bluetooth";
    /**
     * the key used to IP call, and add for new feature IP call prefix
     * the key string must be same as Contacts where send the intent.
     */
    private static final String IPCALL = "ipcall";
    private boolean mIPCall;

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Receiving an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an external ITelephony call in the future.
     * TODO: Keep in sync with the string defined in TwelveKeyDialer.java in Contacts app
     * until this is replaced with the ITelephony API.
     */
    public static final String EXTRA_SEND_EMPTY_FLASH = "com.android.phone.extra.SEND_EMPTY_FLASH";

    // Dialog IDs
    private static final int DIALOG_NOT_VOICE_CAPABLE = 1;

    /**
     * OutgoingCallReceiver finishes NEW_OUTGOING_CALL broadcasts, starting
     * the InCallScreen if the broadcast has not been canceled, possibly with
     * a modified phone number and optional provider info (uri + package name + remote views.)
     */
    public class OutgoingCallReceiver extends BroadcastReceiver {
        private static final String TAG = "OutgoingCallReceiver";

        public void onReceive(Context context, Intent intent) {
            doReceive(context, intent);
            finish();
        }

        public void doReceive(Context context, Intent intent) {
            if (DBG) Log.v(TAG, "doReceive: " + intent);

            boolean alreadyCalled;
            String number;
            String originalUri;

            alreadyCalled = intent.getBooleanExtra(
                    OutgoingCallBroadcaster.EXTRA_ALREADY_CALLED, false);
            if (alreadyCalled) {
                if (DBG) Log.v(TAG, "CALL already placed -- returning.");
                return;
            }

            // Once the NEW_OUTGOING_CALL broadcast is finished, the resultData
            // is used as the actual number to call. (If null, no call will be
            // placed.)

            number = getResultData();
            if (VDBG) Log.v(TAG, "- got number from resultData: '" + number + "'");

            final PhoneApp app = PhoneApp.getInstance();

            if (isOtaActive()) {
                // OTASP call is active. Don't allow new outgoing calls at all
                Log.w(TAG, "OTASP call is active: disallowing a new outgoing call.");
                return;
            }


            if (number == null) {
                if (DBG) Log.v(TAG, "CALL cancelled (null number), returning...");
                return;
            } else if (TelephonyCapabilities.supportsOtasp(app.phone)
                    && (app.phone.getState() != Phone.State.IDLE)
                    && (app.phone.isOtaSpNumber(number))) {
                if (DBG) Log.v(TAG, "Call is active, a 2nd OTA call cancelled -- returning.");
                return;
            } else if (PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, context)) {
                // Just like 3rd-party apps aren't allowed to place emergency
                // calls via the ACTION_CALL intent, we also don't allow 3rd
                // party apps to use the NEW_OUTGOING_CALL broadcast to rewrite
                // an outgoing call into an emergency number.
                Log.w(TAG, "Cannot modify outgoing call to emergency number " + number + ".");
                return;
            }

            originalUri = intent.getStringExtra(
                    OutgoingCallBroadcaster.EXTRA_ORIGINAL_URI);
            if (originalUri == null) {
                Log.e(TAG, "Intent is missing EXTRA_ORIGINAL_URI -- returning.");
                return;
            }

            Uri uri = Uri.parse(originalUri);

            // add for new feature: IP call
            if (mIPCall) {
                String IPPrefix = null;
                IPPrefix = Settings.System.getString(getContentResolver(),
                        Settings.System.IPCALL_PREFIX[mSubscription]);

                if (!TextUtils.isEmpty(IPPrefix)) {
                    number = IPPrefix + number;
                }
            }

            // We already called convertKeypadLettersToDigits() and
            // stripSeparators() way back in onCreate(), before we sent out the
            // NEW_OUTGOING_CALL broadcast.  But we need to do it again here
            // too, since the number might have been modified/rewritten during
            // the broadcast (and may now contain letters or separators again.)
            number = PhoneNumberUtils.convertKeypadLettersToDigits(number);
            number = PhoneNumberUtils.stripSeparators(number);

            if (DBG) Log.v(TAG, "doReceive: proceeding with call...");
            if (VDBG) Log.v(TAG, "- uri: " + uri);
            if (VDBG) Log.v(TAG, "- actual number to dial: '" + number + "'");

            startSipCallOptionHandler(context, intent, uri, number);
        }
    }

    /**
     * cleanup any undismissed ota dialogs so the InCallScreen UI can be shown
     * @return void
     */
    private void otaCleanup() {

        PhoneApp app = PhoneApp.getInstance();
        boolean isOtaCallActive = false;

        if (TelephonyCapabilities.supportsOtasp(app.phone)) {
            boolean activateState = (app.cdmaOtaScreenState.otaScreenState ==
                    OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_ACTIVATION);
            boolean dialogState = (app.cdmaOtaScreenState.otaScreenState ==
                    OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_SUCCESS_FAILURE_DLG);

            if (activateState || dialogState) {
                // The OTASP sequence is active, but either (1) the call
                // hasn't started yet, or (2) the call has ended and we're
                // showing the success/failure screen. In either of these
                // cases it's OK to make a new outgoing call, but we need
                // to take down any OTASP-related UI first.
                if (dialogState)
                    app.dismissOtaDialogs();
                app.clearOtaState();
                app.clearInCallScreenMode();
            }
        }
    }

    /**
     * Check if ota call is active
     * @return
     * True if ota call is still active
     * False if ota call is not active
     */
    private boolean isOtaActive() {

        PhoneApp app = PhoneApp.getInstance();
        boolean isOtaCallActive = false;

        if (TelephonyCapabilities.supportsOtasp(app.phone)) {

            // TODO: Need cleaner way to check if OTA is active.
            // Also, this check seems to be broken in one obscure case: if
            // you interrupt an OTASP call by pressing Back then Skip,
            // otaScreenState somehow gets left in either PROGRESS or
            // LISTENING.
            if ((app.cdmaOtaScreenState.otaScreenState ==
                    OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_PROGRESS)
                    || (app.cdmaOtaScreenState.otaScreenState ==
                    OtaUtils.CdmaOtaScreenState.OtaScreenState.OTA_STATUS_LISTENING)) {
                isOtaCallActive = true;
                // The actual OTASP call is active. Don't allow new
                // outgoing calls at all from this state.
                Log.w(TAG, "OTASP call is active");
            }
        }
        return isOtaCallActive;
    }

    /**
     * Launch the SipCallOptionHandler, which is the next step(*) in the
     * outgoing-call sequence after the outgoing call broadcast is
     * complete.
     *
     * (*) We now know exactly what phone number we need to dial, so the next
     *     step is for the SipCallOptionHandler to decide which Phone type (SIP
     *     or PSTN) should be used.  (Depending on the user's preferences, this
     *     decision may also involve popping up a dialog to ask the user to
     *     choose what type of call this should be.)
     *
     * @param context used for the startActivity() call
     *
     * @param intent the intent from the previous step of the outgoing-call
     *   sequence.  Normally this will be the NEW_OUTGOING_CALL broadcast intent
     *   that came in to the OutgoingCallReceiver, although it can also be the
     *   original ACTION_CALL intent that started the whole sequence (in cases
     *   where we don't do the NEW_OUTGOING_CALL broadcast at all, like for
     *   emergency numbers or SIP addresses).
     *
     * @param uri the data URI from the original CALL intent, presumably either
     *   a tel: or sip: URI.  For tel: URIs, note that the scheme-specific part
     *   does *not* necessarily have separators and keypad letters stripped (so
     *   we might see URIs like "tel:(650)%20555-1234" or "tel:1-800-GOOG-411"
     *   here.)
     *
     * @param number the actual number (or SIP address) to dial.  This is
     *   guaranteed to be either a PSTN phone number with separators stripped
     *   out and keypad letters converted to digits (like "16505551234"), or a
     *   raw SIP address (like "user@example.com").
     */
    private void startSipCallOptionHandler(Context context, Intent intent,
            Uri uri, String number) {
        if (VDBG) {
            Log.i(TAG, "startSipCallOptionHandler...");
            Log.i(TAG, "- intent: " + intent);
            Log.i(TAG, "- uri: " + uri);
            Log.i(TAG, "- number: " + number);
        }

        // Create a copy of the original CALL intent that started the whole
        // outgoing-call sequence.  This intent will ultimately be passed to
        // CallController.placeCall() after the SipCallOptionHandler step.

        Intent newIntent = new Intent(Intent.ACTION_CALL, uri);
        newIntent.putExtra(EXTRA_ACTUAL_NUMBER_TO_DIAL, number);
        newIntent.putExtra(SUBSCRIPTION_KEY, mSubscription);
        PhoneUtils.checkAndCopyPhoneProviderExtras(intent, newIntent);

        // Finally, launch the SipCallOptionHandler, with the copy of the
        // original CALL intent stashed away in the EXTRA_NEW_CALL_INTENT
        // extra.

        Intent selectPhoneIntent = new Intent(ACTION_SIP_SELECT_PHONE, uri);
        selectPhoneIntent.setClass(context, SipCallOptionHandler.class);
        selectPhoneIntent.putExtra(EXTRA_NEW_CALL_INTENT, newIntent);
        selectPhoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (DBG) Log.v(TAG, "startSipCallOptionHandler(): " +
                "calling startActivity: " + selectPhoneIntent);
        context.startActivity(selectPhoneIntent);
        // ...and see SipCallOptionHandler.onCreate() for the next step of the sequence.
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // This method is the single point of entry for the CALL intent,
        // which is used (by built-in apps like Contacts / Dialer, as well
        // as 3rd-party apps) to initiate an outgoing voice call.
        //
        // We also handle two related intents which are only used internally:
        // CALL_PRIVILEGED (which can come from built-in apps like contacts /
        // voice dialer / bluetooth), and CALL_EMERGENCY (from the
        // EmergencyDialer that's reachable from the lockscreen.)
        //
        // The exact behavior depends on the intent's data:
        //
        // - The most typical is a tel: URI, which we handle by starting the
        //   NEW_OUTGOING_CALL broadcast.  That broadcast eventually triggeres
        //   the sequence OutgoingCallReceiver -> SipCallOptionHandler ->
        //   InCallScreen.
        //
        // - Or, with a sip: URI we skip the NEW_OUTGOING_CALL broadcast and
        //   go directly to SipCallOptionHandler, which then leads to the
        //   InCallScreen.
        //
        // - voicemail: URIs take the same path as regular tel: URIs.
        //
        // Other special cases:
        //
        // - Outgoing calls are totally disallowed on non-voice-capable
        //   devices (see handleNonVoiceCapable()).
        //
        // - A CALL intent with the EXTRA_SEND_EMPTY_FLASH extra (and
        //   presumably no data at all) means "send an empty flash" (which
        //   is only meaningful on CDMA devices while a call is already
        //   active.)

        Intent intent = getIntent();
        final Configuration configuration = getResources().getConfiguration();
        //mIPCall = intent.getBooleanExtra(IPCALL, false);
	 mIPCall = intent.getBooleanExtra("ip_call_mode",false) ;
        Log.d(TAG, "outGoingcallBroadCaster is ip call:" + mIPCall);

        if (DBG) Log.v(TAG, "onCreate: this = " + this + ", icicle = " + icicle);
        if (DBG) Log.v(TAG, " - getIntent() = " + intent);
        if (DBG) Log.v(TAG, " - configuration = " + configuration);

        if (icicle != null) {
            // A non-null icicle means that this activity is being
            // re-initialized after previously being shut down.
            //
            // In practice this happens very rarely (because the lifetime
            // of this activity is so short!), but it *can* happen if the
            // framework detects a configuration change at exactly the
            // right moment; see bug 2202413.
            //
            // In this case, do nothing.  Our onCreate() method has already
            // run once (with icicle==null the first time), which means
            // that the NEW_OUTGOING_CALL broadcast for this new call has
            // already been sent.
            Log.i(TAG, "onCreate: non-null icicle!  "
                  + "Bailing out, not sending NEW_OUTGOING_CALL broadcast...");

            // No need to finish() here, since the OutgoingCallReceiver from
            // our original instance will do that.  (It'll actually call
            // finish() on our original instance, which apparently works fine
            // even though the ActivityManager has already shut that instance
            // down.  And note that if we *do* call finish() here, that just
            // results in an "ActivityManager: Duplicate finish request"
            // warning when the OutgoingCallReceiver runs.)

            return;
        }

        // Outgoing phone calls are only allowed on "voice-capable" devices.
        if (!PhoneApp.sVoiceCapable) {
            handleNonVoiceCapable(intent);
            // No need to finish() here; handleNonVoiceCapable() will do
            // that if necessary.
            return;
        }

        /*
         * Clean up any undismissed ota dialogs. If ota call is active outgoing
         * calls will be blocked in OutgoingCallReceiver
         */
        otaCleanup();

        boolean promptEnabled = MSimPhoneFactory.isPromptEnabled();
        String number = PhoneNumberUtils.getNumberFromIntent(intent, this);
        boolean isEccNum = (number != null) && PhoneNumberUtils.isEmergencyNumber(number);
        if (!isEccNum && TelephonyManager.getDefault().isMultiSimEnabled() && /**promptEnabled &&**/
               (activeSubCount() > 1) && (!isIntentFromBluetooth(intent)) &&
                       (!isSIPCall(number, intent))) {
            Log.d(TAG, "Start multisimdialer activity and get the sub selected by user");
            Intent intentMSim = new Intent(this, MSimDialerActivity.class);
            intentMSim.setData(intent.getData());
            intentMSim.setAction(intent.getAction());
            // set subscription for call back
            int subscription = intent.getIntExtra(MSimConstants.SUBSCRIPTION_KEY, -1);
            if (subscription != -1) {
                intentMSim.putExtra(MSimConstants.SUBSCRIPTION_KEY, subscription);
            }

            int requestCode = 1;
            startActivityForResult(intentMSim, requestCode);
        } else {
            // if this call is initiated from the bluetooth handset to redial
            // last call log, we will try to get
            // the original subscription info to MO call
            if (TelephonyManager.getDefault().isMultiSimEnabled() && activeSubCount() > 1
                    && isCallbackPriorityEnabled()) {
                mSubscription = intent.getIntExtra(SUBSCRIPTION_KEY,
                        PhoneApp.getInstance().getVoiceSubscription());
                Log.d(TAG, "active sub count > 1 and use original SLOT:" + mSubscription);
            } else {
                mSubscription = PhoneApp.getInstance().getVoiceSubscription();
                Log.d(TAG, "use default voice sub to call out:" + mSubscription);
            }
            processIntent(intent);
        }
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

    private String RussiaNumber(String numb){
		if(mSubscription == 1 && numb !=null){
			if(numb.startsWith("+7")){
			     StringBuilder temp = new StringBuilder();
				 temp.append("8")  ;
				 temp.append(numb.substring(2)) ;
				 return temp.toString();  
				}
		}
		return numb  ;
	}

	private String LatvianNumber(String numb){
		if(numb !=null){
			if(numb.startsWith("+")){
			     StringBuilder temp = new StringBuilder();
				 temp.append("00")  ;
				 temp.append(numb.substring(1)) ;
				 return temp.toString();  
				}
		}
		return numb  ;
	}

    private void processIntent(Intent intent) {
        String action = intent.getAction();
        intent.putExtra(SUBSCRIPTION_KEY, mSubscription);
        Log.d(TAG, "outGoingcallBroadCaster action is"+ action);
        String number = PhoneNumberUtils.getNumberFromIntent(intent, this);
		 
	 if (Config.FEATURE_PROJECT_RUSSIA_SPECIAL){
	 	number = RussiaNumber(number)  ;
	 }else if(Config.FEATURE_PROJECT_LATVIAN_SPECIAL){
		//number = LatvianNumber(number)  ;
	 }
        Log.d(TAG, " number from Intent : "+ number);
        // Check the number, don't convert for sip uri
        // TODO put uriNumber under PhoneNumberUtils
        if (number != null) {
            if (!PhoneNumberUtils.isUriNumber(number)) {
                number = PhoneNumberUtils.convertKeypadLettersToDigits(number);
                number = PhoneNumberUtils.stripSeparators(number);
            }
        }

        // If true, this flag will indicate that the current call is a special kind
        // of call (most likely an emergency number) that 3rd parties aren't allowed
        // to intercept or affect in any way.  (In that case, we start the call
        // immediately rather than going through the NEW_OUTGOING_CALL sequence.)
        boolean callNow;

        if (getClass().getName().equals(intent.getComponent().getClassName())) {
            // If we were launched directly from the OutgoingCallBroadcaster,
            // not one of its more privileged aliases, then make sure that
            // only the non-privileged actions are allowed.
            if (!Intent.ACTION_CALL.equals(intent.getAction())) {
                Log.w(TAG, "Attempt to deliver non-CALL action; forcing to CALL");
                intent.setAction(Intent.ACTION_CALL);
            }
        }

        // Check whether or not this is an emergency number, in order to
        // enforce the restriction that only the CALL_PRIVILEGED and
        // CALL_EMERGENCY intents are allowed to make emergency calls.
        //
        // (Note that the ACTION_CALL check below depends on the result of
        // isPotentialLocalEmergencyNumber() rather than just plain
        // isLocalEmergencyNumber(), to be 100% certain that we *don't*
        // allow 3rd party apps to make emergency calls by passing in an
        // "invalid" number like "9111234" that isn't technically an
        // emergency number but might still result in an emergency call
        // with some networks.)
        final boolean isExactEmergencyNumber =
                (number != null) && PhoneNumberUtils.isLocalEmergencyNumber(number, this);
        final boolean isPotentialEmergencyNumber =
                (number != null) && PhoneNumberUtils.isPotentialLocalEmergencyNumber(number, this);
        if (VDBG) {
            Log.v(TAG, "- Checking restrictions for number '" + number + "':");
            Log.v(TAG, "    isExactEmergencyNumber     = " + isExactEmergencyNumber);
            Log.v(TAG, "    isPotentialEmergencyNumber = " + isPotentialEmergencyNumber);
        }

        /* Change CALL_PRIVILEGED into CALL or CALL_EMERGENCY as needed. */
        // TODO: This code is redundant with some code in InCallScreen: refactor.
        if (Intent.ACTION_CALL_PRIVILEGED.equals(action)) {
            // We're handling a CALL_PRIVILEGED intent, so we know this request came
            // from a trusted source (like the built-in dialer.)  So even a number
            // that's *potentially* an emergency number can safely be promoted to
            // CALL_EMERGENCY (since we *should* allow you to dial "91112345" from
            // the dialer if you really want to.)
            action = isPotentialEmergencyNumber
                    ? Intent.ACTION_CALL_EMERGENCY
                    : Intent.ACTION_CALL;
            if (DBG) Log.v(TAG, "- updating action from CALL_PRIVILEGED to " + action);
            intent.setAction(action);
        }

        if (Intent.ACTION_CALL.equals(action)) {
            if (isPotentialEmergencyNumber) {
                Log.w(TAG, "Cannot call potential emergency number '" + number
                        + "' with CALL Intent " + intent + ".");
                Log.i(TAG, "Launching default dialer instead...");

                Intent invokeFrameworkDialer = new Intent();

                // TwelveKeyDialer is in a tab so we really want
                // DialtactsActivity.  Build the intent 'manually' to
                // use the java resolver to find the dialer class (as
                // opposed to a Context which look up known android
                // packages only)
                invokeFrameworkDialer.setClassName("com.android.contacts",
                                                   "com.android.contacts.DialtactsActivity");
                invokeFrameworkDialer.setAction(Intent.ACTION_DIAL);
                invokeFrameworkDialer.setData(intent.getData());
                invokeFrameworkDialer.putExtra(SUBSCRIPTION_KEY, mSubscription);

                if (DBG) Log.v(TAG, "onCreate(): calling startActivity for Dialer: "
                               + invokeFrameworkDialer);
                startActivity(invokeFrameworkDialer);
                finish();
                return;
            }
            intent.putExtra(SUBSCRIPTION_KEY, mSubscription);
            Log.d(TAG, "for non emergency call,sub is  :" + mSubscription);
            callNow = false;
        } else if (Intent.ACTION_CALL_EMERGENCY.equals(action)) {
            // ACTION_CALL_EMERGENCY case: this is either a CALL_PRIVILEGED
            // intent that we just turned into a CALL_EMERGENCY intent (see
            // above), or else it really is an CALL_EMERGENCY intent that
            // came directly from some other app (e.g. the EmergencyDialer
            // activity built in to the Phone app.)
            // Make sure it's at least *possible* that this is really an
            // emergency number.
            if (!isPotentialEmergencyNumber) {
                Log.w(TAG, "Cannot call non-potential-emergency number " + number
                        + " with EMERGENCY_CALL Intent " + intent + ".");
                finish();
                return;
            }
            int sub = PhoneApp.getInstance().getVoiceSubscriptionInService();
            intent.putExtra(SUBSCRIPTION_KEY, sub);
            Log.d(TAG, "Attempting emergency call on sub :" + sub);
            callNow = true;
        } else {
            Log.e(TAG, "Unhandled Intent " + intent + ".");
            finish();
            return;
        }

        // Make sure the screen is turned on.  This is probably the right
        // thing to do, and more importantly it works around an issue in the
        // activity manager where we will not launch activities consistently
        // when the screen is off (since it is trying to keep them paused
        // and has...  issues).
        //
        // Also, this ensures the device stays awake while doing the following
        // broadcast; technically we should be holding a wake lock here
        // as well.
        PhoneApp.getInstance().wakeUpScreen();

        /* If number is null, we're probably trying to call a non-existent voicemail number,
         * send an empty flash or something else is fishy.  Whatever the problem, there's no
         * number, so there's no point in allowing apps to modify the number. */
        if (number == null || TextUtils.isEmpty(number)) {
            if (intent.getBooleanExtra(EXTRA_SEND_EMPTY_FLASH, false)) {
                Log.i(TAG, "onCreate: SEND_EMPTY_FLASH...");
                PhoneUtils.sendEmptyFlash(PhoneApp.getInstance().getPhone());
                finish();
                return;
            } else {
                Log.i(TAG, "onCreate: null or empty number, setting callNow=true...");
                callNow = true;
                intent.putExtra(SUBSCRIPTION_KEY, mSubscription);
            }
        }

        if (callNow) {
            // This is a special kind of call (most likely an emergency number)
            // that 3rd parties aren't allowed to intercept or affect in any way.
            // So initiate the outgoing call immediately.

            if (DBG) Log.v(TAG, "onCreate(): callNow case! Calling placeCall(): " + intent);

            // Initiate the outgoing call, and simultaneously launch the
            // InCallScreen to display the in-call UI:
            PhoneApp.getInstance().callController.placeCall(intent);

            // Note we do *not* "return" here, but instead continue and
            // send the ACTION_NEW_OUTGOING_CALL broadcast like for any
            // other outgoing call.  (But when the broadcast finally
            // reaches the OutgoingCallReceiver, we'll know not to
            // initiate the call again because of the presence of the
            // EXTRA_ALREADY_CALLED extra.)
        }

        // For now, SIP calls will be processed directly without a
        // NEW_OUTGOING_CALL broadcast.
        //
        // TODO: In the future, though, 3rd party apps *should* be allowed to
        // intercept outgoing calls to SIP addresses as well.  To do this, we should
        // (1) update the NEW_OUTGOING_CALL intent documentation to explain this
        // case, and (2) pass the outgoing SIP address by *not* overloading the
        // EXTRA_PHONE_NUMBER extra, but instead using a new separate extra to hold
        // the outgoing SIP address.  (Be sure to document whether it's a URI or just
        // a plain address, whether it could be a tel: URI, etc.)
        Uri uri = intent.getData();
        String scheme = uri.getScheme();
        if (Constants.SCHEME_SIP.equals(scheme)
                || PhoneNumberUtils.isUriNumber(number)) {
            startSipCallOptionHandler(this, intent, uri, number);
            finish();
            return;

            // TODO: if there's ever a way for SIP calls to trigger a
            // "callNow=true" case (see above), we'll need to handle that
            // case here too (most likely by just doing nothing at all.)
        }

        final String callOrigin = intent.getStringExtra(PhoneApp.EXTRA_CALL_ORIGIN);
        if (callOrigin != null) {
            if (DBG) Log.v(TAG, "Call origin is passed (" + callOrigin + ")");
            PhoneApp.getInstance().setLatestActiveCallOrigin(callOrigin);
        } else {
            if (DBG) Log.v(TAG, "Call origin is not passed. Reset current one.");
            PhoneApp.getInstance().setLatestActiveCallOrigin(null);
        }

        Intent broadcastIntent = new Intent(Intent.ACTION_NEW_OUTGOING_CALL);
        if (number != null) {
            broadcastIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);
        }
        PhoneUtils.checkAndCopyPhoneProviderExtras(intent, broadcastIntent);
        broadcastIntent.putExtra(EXTRA_ALREADY_CALLED, callNow);
        broadcastIntent.putExtra(EXTRA_ORIGINAL_URI, uri.toString());
        broadcastIntent.putExtra(SUBSCRIPTION_KEY, mSubscription);

        if (DBG) Log.v(TAG, "Broadcasting intent: " + broadcastIntent + ".");
        sendOrderedBroadcast(broadcastIntent, PERMISSION, new OutgoingCallReceiver(),
                             null,  // scheduler
                             Activity.RESULT_OK,  // initialCode
                             number,  // initialData: initial value for the result data
                             null);  // initialExtras
    }

    @Override
    protected void onStop() {
        // Clean up (and dismiss if necessary) any managed dialogs.
        //
        // We don't do this in onPause() since we can be paused/resumed
        // due to orientation changes (in which case we don't want to
        // disturb the dialog), but we *do* need it here in onStop() to be
        // sure we clean up if the user hits HOME while the dialog is up.
        //
        // Note it's safe to call removeDialog() even if there's no dialog
        // associated with that ID.
        removeDialog(DIALOG_NOT_VOICE_CAPABLE);

        super.onStop();
    }

    /**
     * Handle the specified CALL or CALL_* intent on a non-voice-capable
     * device.
     *
     * This method may launch a different intent (if there's some useful
     * alternative action to take), or otherwise display an error dialog,
     * and in either case will finish() the current activity when done.
     */
    private void handleNonVoiceCapable(Intent intent) {
        if (DBG) Log.v(TAG, "handleNonVoiceCapable: handling " + intent
                       + " on non-voice-capable device...");
        String action = intent.getAction();
        Uri uri = intent.getData();
        String scheme = uri.getScheme();

        // Handle one special case: If this is a regular CALL to a tel: URI,
        // bring up a UI letting you do something useful with the phone number
        // (like "Add to contacts" if it isn't a contact yet.)
        //
        // This UI is provided by the contacts app in response to a DIAL
        // intent, so we bring it up here by demoting this CALL to a DIAL and
        // relaunching.
        //
        // TODO: it's strange and unintuitive to manually launch a DIAL intent
        // to do this; it would be cleaner to have some shared UI component
        // that we could bring up directly.  (But for now at least, since both
        // Contacts and Phone are built-in apps, this implementation is fine.)

        if (Intent.ACTION_CALL.equals(action) && (Constants.SCHEME_TEL.equals(scheme))) {
            Intent newIntent = new Intent(Intent.ACTION_DIAL, uri);
            if (DBG) Log.v(TAG, "- relaunching as a DIAL intent: " + newIntent);
            startActivity(newIntent);
            finish();
            return;
        }

        // In all other cases, just show a generic "voice calling not
        // supported" dialog.
        showDialog(DIALOG_NOT_VOICE_CAPABLE);
        // ...and we'll eventually finish() when the user dismisses
        // or cancels the dialog.
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
            case DIALOG_NOT_VOICE_CAPABLE:
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.not_voice_capable)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok, this)
                        .setOnCancelListener(this)
                        .create();
                break;
            default:
                Log.w(TAG, "onCreateDialog: unexpected ID " + id);
                dialog = null;
                break;
        }
        return dialog;
    }

    // DialogInterface.OnClickListener implementation
    public void onClick(DialogInterface dialog, int id) {
        // DIALOG_NOT_VOICE_CAPABLE is the only dialog we ever use (so far
        // at least), and its only button is "OK".
        finish();
    }

    // DialogInterface.OnCancelListener implementation
    public void onCancel(DialogInterface dialog) {
        // DIALOG_NOT_VOICE_CAPABLE is the only dialog we ever use (so far
        // at least), and canceling it is just like hitting "OK".
        finish();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Collect subscription data from the intent and use it
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "activity cancelled or backkey pressed ");
            finish();
        } else if (resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            mSubscription = extras.getInt(SUBSCRIPTION_KEY);
            Log.d(TAG, "subscription selected from multiSimDialer" + mSubscription);
            processIntent(data);
        }
    }

    private int activeSubCount() {
        SubscriptionManager subManager = SubscriptionManager.getInstance();
        int count = subManager.getActiveSubscriptionsCount();
        if (DBG) Log.v(TAG, "count of subs activated " + count);
        return count;
    }

    private boolean  isIntentFromBluetooth(Intent intent) {
        boolean btIntent = false;
        Bundle extras = intent.getExtras();
        if (extras != null) {
            if ((extras.getString(BLUETOOTH) != null) && (extras.getString(BLUETOOTH).equals("true"))) {
                btIntent = true;
                Log.d(TAG, "isIntentFromBluetooth " + btIntent + "intent :"+ extras.getString(BLUETOOTH));
            }
        }
        return btIntent;
    }

    private boolean isSIPCall(String number, Intent intent) {
        boolean sipCall = false;
        String scheme = "";
        if (intent.getData() != null) {
            scheme =  intent.getData().getScheme();
            if ((scheme != null) && ("sip".equals(scheme) || PhoneNumberUtils.isUriNumber(number))) {
                sipCall = true;
            }
        }
        Log.d(TAG, "isSIPCall : " + sipCall);
        return sipCall;
    }

    // Implement onConfigurationChanged() purely for debugging purposes,
    // to make sure that the android:configChanges element in our manifest
    // is working properly.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (DBG) Log.v(TAG, "onConfigurationChanged: newConfig = " + newConfig);
    }
}
