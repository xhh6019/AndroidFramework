/*
 * Copyright (C) 2010 The Android Open Source Project
 * Copyright (C) 2012, Code Aurora Forum. All rights reserved.
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

package com.android.contacts.interactions;

import com.android.contacts.R;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.ContactSaveService;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.AccountWithDataSet;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.vcard.ExportVCardActivity;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.editor.MultiPickContactActivity;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.SimContactsOperation;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionManager;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.telephony.TelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.content.ContentUris;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.text.TextUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;
import java.util.Set;

import android.app.Activity;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportExportDialogFragment extends DialogFragment
        implements SelectAccountDialogFragment.Listener {
    public static final String TAG = "ImportExportDialogFragment";

    private static final String KEY_RES_ID = "resourceId";

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, //0
        Phone.TYPE, //1
        Phone.LABEL, //2
        Phone.NUMBER, //3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
    };

    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_TYPE_COLUMN_INDEX = 1;
    static final int PHONE_LABEL_COLUMN_INDEX = 2;
    static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;

    // This value needs to start at 7. See {@link PeopleActivity}.
    public static final int SUBACTIVITY_MULTI_PICK_CONTACT = 7;

    private static final int SUB1 = 0;
    private static final int SUB2 = 1;
    private static final int SUB_INVALID = -1;

    public static final String SUBSCRIPTION = "sub_id";  // subscription column key
    public static final String ACTION_MULTI_PICK = "com.android.contacts.action.MULTI_PICK";   // multi pick contacts action
    public static final String ACTION_MULTI_PICK_EMAIL = "com.android.contacts.action.MULTI_PICK_EMAIL";  // multi-pick contacts which contains email address

    //TODO: we need to refactor the export code in future release.
    public static int mExportSub;                   // QRD enhancement: export subscription selected by user

    // QRD enhancement: Toast handler for exporting concat to sim card
    private static final int TOAST_EXPORT_FAILED = 0;
    private static final int TOAST_EXPORT_FINISHED = 1;


    private SimContactsOperation mSimContactsOperation;
    private String name = null;
    private String number = null;


    /** Preferred way to show this dialog */
    public static ImportExportDialogFragment show(FragmentManager fragmentManager) {
        final ImportExportDialogFragment fragment = new ImportExportDialogFragment();
        fragment.show(fragmentManager, ImportExportDialogFragment.TAG);
        return fragment;
    }

    private String getMultiSimName(int subscription) {
        return Settings.System.getString(getActivity().getContentResolver(),
            Settings.System.MULTI_SIM_NAME[subscription]);
    }

    Activity mactiv;


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        mactiv = getActivity();
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                final int resId = getItem(position);
                result.setText(resId);
                return result;
            }
        };

        boolean hasIccCard = false;
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            hasIccCard = MSimTelephonyManager.getDefault().hasIccCard(i);
            if (hasIccCard) {
               break;
            }
        }

        if (MSimTelephonyManager.getDefault().hasIccCard()
                && res.getBoolean(R.bool.config_allow_sim_import)) {
             if (TelephonyManager.getDefault().isMultiSimEnabled()) {
		adapter.add(R.string.import_from_sim_uim);
		}else{
		adapter.add(R.string.import_from_uim);
             	}
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (hasIccCard) {
		if (TelephonyManager.getDefault().isMultiSimEnabled()) {
		adapter.add(R.string.export_to_sim_uim);
		}else{
		adapter.add(R.string.export_to_uim);
		}
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            adapter.add(R.string.export_to_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            adapter.add(R.string.share_visible_contacts);
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_uim: 
                    case R.string.import_from_sim_uim:{
                        dismissDialog = true;
                        handleImportFromSimRequest(resId);
                        break;
                        }
                    case R.string.import_from_sdcard: {
                        dismissDialog = handleImportRequest(resId);
                        break;
                    }
		      case R.string.export_to_uim:
		      case R.string.export_to_sim_uim:  {
                        dismissDialog = true;
                        handleExportToSimRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        dismissDialog = true;
                        Intent exportIntent = new Intent(getActivity(), ExportVCardActivity.class);
                        getActivity().startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        dismissDialog = true;
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        dismissDialog = true;
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.dialog_import_export)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void doShareVisibleContacts() {
        // TODO move the query into a loader and do this in a background thread
        final Cursor cursor = getActivity().getContentResolver().query(Contacts.CONTENT_URI,
                LOOKUP_PROJECTION, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
        if (cursor != null) {
            try {
                if (!cursor.moveToFirst()) {
                    Toast.makeText(getActivity(), R.string.share_error, Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder uriListBuilder = new StringBuilder();
                int index = 0;
                do {
                    if (index != 0)
                        uriListBuilder.append(':');
                    uriListBuilder.append(cursor.getString(0));
                    index++;
                } while (cursor.moveToNext());
                Uri uri = Uri.withAppendedPath(
                        Contacts.CONTENT_MULTI_VCARD_URI,
                        Uri.encode(uriListBuilder.toString()));

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                getActivity().startActivity(intent);
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * Handle "import from SIM" and "import from SD".
     *
     * @return {@code true} if the dialog show be closed.  {@code false} otherwise.
     */
    private boolean handleImportRequest(int resId) {
        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mactiv);
        final List<AccountWithDataSet> accountList = accountTypes.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            switch (resId) {
		  case R.string.import_from_uim: 
		  case R.string.import_from_sim_uim:
                case R.string.import_from_sdcard:
                    SelectAccountDialogFragment.show(
                        mactiv.getFragmentManager(), this,
                        R.string.dialog_new_contact_account,
                        AccountListFilter.ACCOUNTS_CONTACT_WRITABLE_WITHOUT_SIM, args);
                    return false;
            }
            SelectAccountDialogFragment.show(
                    mactiv.getFragmentManager(), this,
                    R.string.dialog_new_contact_account,
                    AccountListFilter.ACCOUNTS_CONTACT_WRITABLE, args);

            // In this case, because this DialogFragment is used as a target fragment to
            // SelectAccountDialogFragment, we can't close it yet.  We close the dialog when
            // we get a callback from it.
            return false;
        }

        AccountSelectionUtil.doImport(mactiv, resId,
                (size == 1 ? accountList.get(0) : null));
        return true; // Close the dialog.
    }

    /**
     * Called when an account is selected on {@link SelectAccountDialogFragment}.
     */
    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(mactiv, extraArgs.getInt(KEY_RES_ID), account);

        // At this point the dialog is still showing (which is why we can use getActivity() above)
        // So close it.
        dismiss();
    }

    @Override
    public void onAccountSelectorCancelled() {
        // See onAccountChosen() -- at this point the dialog is still showing.  Close it.
        dismiss();
    }

    private class ExportToSimSelectListener implements DialogInterface.OnClickListener{
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                mExportSub = which;
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent pickPhoneIntent = new Intent(ACTION_MULTI_PICK, Contacts.CONTENT_URI);
                mactiv.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
            }
        }
    }

    public class ImportFromSimSelectListener implements DialogInterface.OnClickListener{
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0) {
                AccountSelectionUtil.setImportSubscription(which);
            } else if (which == DialogInterface.BUTTON_POSITIVE) {
                   if (TelephonyManager.getDefault().isMultiSimEnabled()) {
			handleImportRequest(R.string.import_from_sim_uim);
	        	}else{
			handleImportRequest(R.string.import_from_uim);
               	}
            }
        }
    }

    private String getAccountNameBy(int subscription) {
        String accountName = null;
        if (!TelephonyManager.getDefault().isMultiSimEnabled()) {
            accountName = SimContactsConstants.SIM_NAME;
        } else {
            if (subscription == 0)
                accountName = SimContactsConstants.SIM_NAME_1;
            else if (subscription == 1)
                accountName = SimContactsConstants.SIM_NAME_2;
        }
        return accountName;
    }

    private  void actuallyImportOneSimContact(
        final ContentValues values, final ContentResolver resolver, int subscription) {

        ContentValues sEmptyContentValues = new ContentValues();
        final String name = values.getAsString(SimContactsConstants.STR_TAG);
        final String phoneNumber = values.getAsString(SimContactsConstants.STR_NUMBER);
        final String emailAddresses = values.getAsString(SimContactsConstants.STR_EMAILS);
        final String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        final String[] emailAddressArray;
        final String[] anrArray;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(",");
        } else {
            anrArray = null;
        }
        Log.d(TAG," actuallyImportOneSimContact: name= " + name +
            ", phoneNumber= " + phoneNumber +", emails= "+ emailAddresses
            +", anrs= "+ anrs + ", sub " + subscription);

        String accountName = getAccountNameBy(subscription);
        String accountType = SimContactsConstants.ACCOUNT_TYPE_SIM;

        final ArrayList<ContentProviderOperation> operationList =
            new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.ACCOUNT_NAME, accountName);
        builder.withValue(RawContacts.ACCOUNT_TYPE, accountType);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
        builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
        builder.withValue(Phone.NUMBER, phoneNumber);
        builder.withValue(Data.IS_PRIMARY, 1);
        operationList.add(builder.build());

        if (anrArray != null) {
            for (String anr :anrArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                builder.withValue(Phone.NUMBER, anr);
                //builder.withValue(Data.IS_PRIMARY, 1);
                operationList.add(builder.build());
            }
        }

        if (emailAddresses != null) {
            for (String emailAddress : emailAddressArray) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                builder.withValue(Email.ADDRESS, emailAddress);
                operationList.add(builder.build());
            }
        }

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG,String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    /**
     * A thread that export contacts to sim card
     */
    public class ExportToSimThread extends Thread {
        public static final int TYPE_ALL = 1;
        public static final int TYPE_SELECT = 2;
        private int subscription;
        private int type;
        private boolean canceled;
        private ArrayList<String[]> contactList;
        private ProgressDialog mExportProgressDlg;
        private ContentValues mValues = new ContentValues();
        PeopleActivity mpeople;

        public ExportToSimThread(int type, int subscription, ArrayList<String[]> contactList, PeopleActivity mpactiv) {
            super();
            this.type = type;
            this.subscription = subscription;
            this.contactList = contactList;
            canceled = false;
            mpeople = mpactiv;
            setExportProgress(contactList.size());
        }

        @Override
        public void run() {
            String accountName = getAccountNameBy(subscription);
            String accountType = SimContactsConstants.ACCOUNT_TYPE_SIM;
            Account account = new Account(accountName,accountType);
            //GoogleSource.createMyContactsIfNotExist(account, getActivity());

            mSimContactsOperation = new SimContactsOperation(mpeople);

            // call query first, otherwise insert will fail if this insert is called without any query before
            if (subscription == SUB1) {
                mpeople.getContentResolver().query(Uri.parse("content://iccmsim/adn"), null, null, null, null);
            } else if (subscription == SUB2) {
                mpeople.getContentResolver().query(Uri.parse("content://iccmsim/adn_sub2"), null, null, null, null);
            } else {
                mpeople.getContentResolver().query(Uri.parse("content://icc/adn"), null, null, null, null);
            }
            if (type == TYPE_SELECT) {
                if (contactList != null) {
                    Iterator<String[]> iterator = contactList.iterator();
                    Uri result;
                    while (iterator.hasNext() && !canceled) {
                        String[] contactInfo = iterator.next();
                        result = insert(contactInfo[0], contactInfo[1], subscription);
                        if (result == null) {
                            // continue;   Modify By Michael.Chan  @ Apr 15 2013  
				break ; 
                        } else {
                            Log.d(TAG, "Exported contact [" + contactInfo[0] + ", " + contactInfo[1]
                                + "] to sub " + subscription);
                        }
                    }
                }
            } else if (type == TYPE_ALL) {
                // Not use now.
                Cursor cursor = mpeople.getContentResolver().query(Phone.CONTENT_URI,
                        PHONES_PROJECTION, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext() && !canceled) {
                        insert(cursor.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX),
                               cursor.getString(PHONE_NUMBER_COLUMN_INDEX), subscription);
                    }
                    cursor.close();
                }
            }
            if (mExportProgressDlg != null) {
                mExportProgressDlg.dismiss();
                mExportProgressDlg = null;
            }
            mToastHandler.sendEmptyMessage(TOAST_EXPORT_FINISHED);
        }

        private Uri insert(String name, String number, int subscription) {
            Uri result;
            mValues.clear();
            mValues.put("tag", name);
            mValues.put("number", PhoneNumberUtils.stripSeparators(number));

            result = mSimContactsOperation.insert(mValues,subscription);


            if (result != null){
                // we should import the contact to the sim account at the same time.
                actuallyImportOneSimContact(mValues, mpeople.getContentResolver(),subscription);
                if (mExportProgressDlg != null) {
                    mExportProgressDlg.incrementProgressBy(1);
                }
            } else {
                Log.e(TAG, "export contact: [" + name + ", " + number + "] to slot "
                    + subscription + " failed");
                mToastHandler.sendEmptyMessage(TOAST_EXPORT_FAILED);
            }
            return result;
        }



        private void setExportProgress(int size){
            mExportProgressDlg = new ProgressDialog(mpeople);
	     mExportProgressDlg.setTitle(R.string.export_to_uim);
            mExportProgressDlg.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "Cancel exporting contacts");
                    canceled = true;
                }
            });
            mExportProgressDlg.setMessage(mpeople.getString(R.string.exporting));
            mExportProgressDlg.setProgressNumberFormat(mpeople.getString(R.string.reading_vcard_files));
            mExportProgressDlg.setMax(size);
            mExportProgressDlg.setProgress(0);
            mExportProgressDlg.show();
        }

        private Handler mToastHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case TOAST_EXPORT_FAILED:
                        Toast.makeText(mpeople, R.string.export_failed, Toast.LENGTH_LONG).show();
                        break;
                    case TOAST_EXPORT_FINISHED:
                        Toast.makeText(mpeople, R.string.export_finished, Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };

    }

    Uri mSelectedContactUri;
    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            // TODO: need to consider USIM
            if (mSelectedContactUri != null) {
                long id = ContentUris.parseId(mSelectedContactUri);
                int sub = mSimContactsOperation.getSimSubscription(id);
                if (sub != -1) {
                    ContentValues values =
                        mSimContactsOperation.getSimAccountValues(id);
                    int result = mSimContactsOperation.delete(values,sub);
                    if (result == 1)
                    getActivity().getContentResolver().delete(mSelectedContactUri, null, null);
                } else {
                    getActivity().getContentResolver().delete(mSelectedContactUri, null, null);
                }
            }
        }
    }

    private int activeSubCount() {
        int count = 0;
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            if (MSimTelephonyManager.SIM_STATE_ABSENT != MSimTelephonyManager.getDefault()
                    .getSimState(i))
                count++;
        }
        Log.d(TAG, "active count:" + count);
        return count;
    }

    public ImportFromSimSelectListener listener;
    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private Dialog displayImportExportDialog(int id, Bundle bundle) {
    Dialog diag;
        switch (id) {
	     case R.string.import_from_uim: 
	     case R.string.import_from_sim_uim:
            case R.string.import_from_sim_select: {
                if (activeSubCount() == 2) {
                    listener = new ImportFromSimSelectListener();
                    ((PeopleActivity) getActivity()).showSimSelectDialog();
                } else if (activeSubCount() == 1) {
                    AccountSelectionUtil.setImportSubscription(MSimTelephonyManager
                            .getDefault().getPreferredVoiceSubscription());
			if (TelephonyManager.getDefault().isMultiSimEnabled()) {
			handleImportRequest(R.string.import_from_sim_uim);
	        	}else{
			handleImportRequest(R.string.import_from_uim);
               	}
                }
                break;
            }
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(getActivity(), id);
            }
            case R.string.export_to_sim_uim:
	     case R.string.export_to_uim: {
                String[] items = new String[TelephonyManager.getDefault().getPhoneCount()];
                for (int i = 0; i < items.length; i++) {
			items[i] = getString(R.string.export_to_uim) + ": " + getMultiSimName(i);
                }
                mExportSub = 0;
                ExportToSimSelectListener listener = new ExportToSimSelectListener();
                return new AlertDialog.Builder(getActivity())
                        .setTitle(setSimUimString(R.string.export_to_sim_uim))
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setSingleChoiceItems(items, 0, listener).create();
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_hide_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_delete_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_multiple_contact_delete_confirmation: {
                return new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
        }
        return null;
    }

    private String setSimUimString(int id){
	       String SimString = getString(id) ;
		if(SimString.contains("SIM/UIM")){
			String[] SimUimString = SimString.split("SIM/UIM");
			if(TelephonyManager.getDefault().isMultiSimEnabled()){
			return SimUimString[0]+"SIM/UIM"+SimUimString[1]  ;
			}else{
			return SimUimString[0]+"UIM"+SimUimString[1]  ;
			}
		}else if(SimString.contains("UIM")){
			String[] SimUimString = SimString.split("UIM");
			if(TelephonyManager.getDefault().isMultiSimEnabled()){
			return SimUimString[0]+"SIM/UIM"+SimUimString[1]  ;
			}else{
			return SimUimString[0]+"UIM"+SimUimString[1]  ;
			}
		}
		return SimString;
    	}


    private void handleImportFromSimRequest(int Id) {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            if (hasMultiEnabledIccCard()) {
                displayImportExportDialog(R.string.import_from_sim_select
                ,null);
            } else {
                AccountSelectionUtil.setImportSubscription(getEnabledIccCard());
                handleImportRequest(Id);
            }
        } else {
            handleImportRequest(Id);
        }
    }

    private void handleExportToSimRequest(int Id) {
        if (hasMultiEnabledIccCard()) {
            //has two enalbed sim cards, prompt dialog to select one
            displayImportExportDialog(Id, null).show();
        } else {
            mExportSub = getEnabledIccCard();
            Intent pickPhoneIntent = new Intent(ACTION_MULTI_PICK, Contacts.CONTENT_URI);
            // do not show the contacts in SIM card
            pickPhoneIntent.putExtra(MultiPickContactActivity.EXT_NOT_SHOW_SIM_FLAG, true);
            mactiv.startActivityForResult(pickPhoneIntent, SUBACTIVITY_MULTI_PICK_CONTACT);
        }
    }

    private boolean hasEnabledIccCard(int subscription) {
        return MSimTelephonyManager.getDefault().hasIccCard(subscription) &&
                   MSimTelephonyManager.getDefault().getSimState(subscription) == MSimTelephonyManager.SIM_STATE_READY;
    }

    private boolean hasMultiEnabledIccCard() {
        int count = 0;
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            boolean hasEnabledIccCard = hasEnabledIccCard(i);
            if (hasEnabledIccCard) {
                count++;
            }
        }
        return count > 1;
    }

    private boolean hasEnabledIccCard() {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            boolean hasEnabledIccCard = false;
            for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
                hasEnabledIccCard = hasEnabledIccCard(i);
                if (hasEnabledIccCard) {
                    break;
                }
            }
            if (hasEnabledIccCard) {
                return true;
            } else {
                return false;
            }
        } else {
            return TelephonyManager.getDefault().hasIccCard();
        }
    }

    private int getEnabledIccCard() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            boolean hasEnabledIccCard = hasEnabledIccCard(i);
            if (hasEnabledIccCard) {
                return i;
            }
        }
        return 0;
    }

    private int mDisplayOrder;

    public int getSummaryDisplayNameColumnIndex() {
        if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return MultiPickContactActivity.SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        } else {
            return MultiPickContactActivity.SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        }
    }

    /**
     * search shotcut name
     */
    private String searchShotcutName(ContentResolver resolver, Uri lookupUri) {
        if (lookupUri == null) {
            return null;
        }

        String[] projection = MultiPickContactActivity.getProjectionForQuery();
        Cursor c = resolver.query(lookupUri,projection, null, null, null);
        if (c == null) {
            return null;
        }

        try {
            if (c.moveToFirst()) {
                String shotcutName = c.getString(getSummaryDisplayNameColumnIndex());
                Log.d(TAG, "searchShotcutName:shotcutName"+shotcutName);
                return shotcutName;
            }
        } finally {
            c.close();
        }
        return null;
    }

}
