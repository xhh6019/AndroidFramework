/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
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
 *
 */

package com.android.launcher2;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import android.util.Log;

import com.android.launcher.R;

public class UpdateShortcutReceiver extends BroadcastReceiver {
    public static final String ACTION_UPDATE_SHORTCUT =
            "com.android.launcher.action.UPDATE_SHORTCUT";

    public static final String EXTRA_SHORTCUT_NEWNAME = "com.android.launcher.extra.shortcut.NEWNAME";

    public void onReceive(Context context, Intent data) {
        if (!ACTION_UPDATE_SHORTCUT.equals(data.getAction())) {
            return;
        }

        if (data.getStringExtra(EXTRA_SHORTCUT_NEWNAME) != null){
            updateShortcutTitle(context, data);
        }
    }

    private boolean updateShortcutTitle(Context context, Intent data) {
        String original_name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        String name = data.getStringExtra(EXTRA_SHORTCUT_NEWNAME);

        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent != null) {
            if (intent.getAction() == null) {
                intent.setAction(Intent.ACTION_VIEW);
            }
            if (!LauncherModel.shortcutExists(context, original_name, intent)){
                return false;
            } else {
                LauncherApplication app = (LauncherApplication) context.getApplicationContext();
                app.getModel().updateItemTitleInDatabase(context, original_name, name, intent);
            }
            return true;
        }
        return false;
    }
}
