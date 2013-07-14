/*
 * Copyright (C) 2012 JOYI 
 *
 * for bug11223@HK6186;
 */

package com.android.gallery3d.app;

import android.util.Log;
import android.widget.Toast;

public class ToastMaster {

    private static Toast sToast = null;
	private static String TAG = "ToastMaster";
    private ToastMaster() {

    }

    public static void setToast(Toast toast) {
		
        if (sToast != null)
    	{
            sToast.cancel();
    	}
        sToast = toast;
		
    }

    public static void cancelToast() {
        if (sToast != null)
            sToast.cancel();
        sToast = null;
    }

}
