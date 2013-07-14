/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.dialpad;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.text.TextWatcher;
import android.text.Editable;
import android.text.TextPaint;

import android.util.Log;



/**
 * EditText which suppresses IME show up.
 */
public class DigitsEditText extends EditText implements TextWatcher{
       private boolean mEllipsized = false;
	private float mMaxTextSize = 45.5F;
	private float mMinTextSize = 24.0F;
	private float mScaleTextSize = 1.0F;
	private float mScaledDensity = 1.5F;
	private float mScaledWidth = 480.0F;
	private float mDeleteWidth = 120.0F;
	private float mWidth;

    public DigitsEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setInputType(getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
	 setSoftInputShownOnFocus(false);
	 }

	public void afterTextChanged(Editable paramEditable)
	{
	}

	public void beforeTextChanged(CharSequence paramCharSequence, int paramInt1, int paramInt2, int paramInt3)
	{
	}

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        final InputMethodManager imm = ((InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
        }
    }

	public void onTextChanged(CharSequence paramCharSequence, int paramInt1, int paramInt2, int paramInt3)
	  {
		super.onTextChanged(paramCharSequence, paramInt1, paramInt2, paramInt3);
		if(getText() != null){
			
			}else{
			}
		    float mScreenSize = this.mScaledWidth;
		    float mLeft = getPaddingLeft();
		    float mRight = getPaddingRight();
		    float mTextSize= getTextSize();
		    float mDialSize  = mScreenSize - this.mDeleteWidth  ;
 		    float mCharNum = paramCharSequence.length()  ;
		    float mRevangeCharSize = mDialSize/mCharNum  ; 
		    if(mMinTextSize>mRevangeCharSize){
				setTextSize(mMinTextSize)  ;
		    	}else if(mMaxTextSize<mRevangeCharSize){
			       setTextSize(mMaxTextSize)  ;
		    	}else{
		    	       setTextSize(mRevangeCharSize)  ;
		    	}
	}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final boolean ret = super.onTouchEvent(event);
        // Must be done after super.onTouchEvent()
        final InputMethodManager imm = ((InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE));
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(getApplicationWindowToken(), 0);
        }
        return ret;
    }

    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // Since we're replacing the text every time we add or remove a
            // character, only read the difference. (issue 5337550)
            final int added = event.getAddedCount();
            final int removed = event.getRemovedCount();
            final int length = event.getBeforeText().length();
            if (added > removed) {
                event.setRemovedCount(0);
                event.setAddedCount(1);
                event.setFromIndex(length);
            } else if (removed > added) {
                event.setRemovedCount(1);
                event.setAddedCount(0);
                event.setFromIndex(length - 1);
            } else {
                return;
            }
        } else if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // The parent EditText class lets tts read "edit box" when this View has a focus, which
            // confuses users on app launch (issue 5275935).
            return;
        }
        super.sendAccessibilityEventUnchecked(event);
    }
}