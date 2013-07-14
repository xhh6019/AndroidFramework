/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.inputmethod.pinyin;

import com.android.inputmethod.pinyin.PinyinIME.DecodingInfo;

import java.util.Vector;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * View to show candidate list. There two candidate view instances which are
 * used to show animation when user navigates between pages.
 */
public class CandidateView extends View {
    /**
     * The minimum width to show a item.
     */
    private static final float MIN_ITEM_WIDTH = 22;

    /**
     * Suspension points used to display long items.
     */
    private static final String SUSPENSION_POINTS = "...";

    /**
     * The width to draw candidates.
     */
    private int mContentWidth;

    /**
     * The height to draw candidate content.
     */
    private int mContentHeight;

    /**
     * Whether footnotes are displayed. Footnote is shown when hardware keyboard
     * is available.
     */
    private boolean mShowFootnote = true;

    /**
     * Balloon hint for candidate press/release.
     */
    private BalloonHint mBalloonHint;

    /**
     * Desired position of the balloon to the input view.
     */
    private int mHintPositionToInputView[] = new int[2];

    /**
     * Decoding result to show.
     */
    private DecodingInfo mDecInfo;

    /**
     * Listener used to notify IME that user clicks a candidate, or navigate
     * between them.
     */
    private CandidateViewListener mCvListener;

    /**
     * Used to notify the container to update the status of forward/backward
     * arrows.
     */
    private ArrowUpdater mArrowUpdater;

    /**
     * If true, update the arrow status when drawing candidates.
     */
    private boolean mUpdateArrowStatusWhenDraw = false;

    /**
     * Page number of the page displayed in this view.
     */
    private int mPageNo;

    /**
     * Active candidate position in this page.
     */
    private int mActiveCandInPage;

    /**
     * Used to decided whether the active candidate should be highlighted or
     * not. If user changes focus to composing view (The view to show Pinyin
     * string), the highlight in candidate view should be removed.
     */
    private boolean mEnableActiveHighlight = true;

    /**
     * The page which is just calculated.
     */
    private int mPageNoCalculated = -1;

    /**
     * The Drawable used to display as the background of the high-lighted item.
     */
    private Drawable mActiveCellDrawable;

    /**
     * The Drawable used to display as separators between candidates.
     */
    private Drawable mSeparatorDrawable;

    /**
     * Color to draw normal candidates generated by IME.
     */
    private int mImeCandidateColor;

    /**
     * Color to draw normal candidates Recommended by application.
     */
    private int mRecommendedCandidateColor;

    /**
     * Color to draw the normal(not highlighted) candidates, it can be one of
     * {@link #mImeCandidateColor} or {@link #mRecommendedCandidateColor}.
     */
    private int mNormalCandidateColor;

    /**
     * Color to draw the active(highlighted) candidates, including candidates
     * from IME and candidates from application.
     */
    private int mActiveCandidateColor;

    /**
     * Text size to draw candidates generated by IME.
     */
    private int mImeCandidateTextSize;

    /**
     * Text size to draw candidates recommended by application.
     */
    private int mRecommendedCandidateTextSize;

    /**
     * The current text size to draw candidates. It can be one of
     * {@link #mImeCandidateTextSize} or {@link #mRecommendedCandidateTextSize}.
     */
    private int mCandidateTextSize;

    /**
     * Paint used to draw candidates.
     */
    private Paint mCandidatesPaint;

    /**
     * Used to draw footnote.
     */
    private Paint mFootnotePaint;

    /**
     * The width to show suspension points.
     */
    private float mSuspensionPointsWidth;

    /**
     * Rectangle used to draw the active candidate.
     */
    private RectF mActiveCellRect;

    /**
     * Left and right margins for a candidate. It is specified in xml, and is
     * the minimum margin for a candidate. The actual gap between two candidates
     * is 2 * {@link #mCandidateMargin} + {@link #mSeparatorDrawable}.
     * getIntrinsicWidth(). Because length of candidate is not fixed, there can
     * be some extra space after the last candidate in the current page. In
     * order to achieve best look-and-feel, this extra space will be divided and
     * allocated to each candidates.
     */
    private float mCandidateMargin;

    /**
     * Left and right extra margins for a candidate.
     */
    private float mCandidateMarginExtra;

    /**
     * Rectangles for the candidates in this page.
     **/
    private Vector<RectF> mCandRects;

    /**
     * FontMetricsInt used to measure the size of candidates.
     */
    private FontMetricsInt mFmiCandidates;

    /**
     * FontMetricsInt used to measure the size of footnotes.
     */
    private FontMetricsInt mFmiFootnote;

    private PressTimer mTimer = new PressTimer();

    private GestureDetector mGestureDetector;

    private int mLocationTmp[] = new int[2];

    public CandidateView(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources r = context.getResources();

        Configuration conf = r.getConfiguration();
        if (conf.keyboard == Configuration.KEYBOARD_NOKEYS
                || conf.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            mShowFootnote = false;
        }

        mActiveCellDrawable = r.getDrawable(R.drawable.candidate_hl_bg);
        mSeparatorDrawable = r.getDrawable(R.drawable.candidates_vertical_line);
        mCandidateMargin = r.getDimension(R.dimen.candidate_margin_left_right);

        mImeCandidateColor = r.getColor(R.color.candidate_color);
        mRecommendedCandidateColor = r.getColor(R.color.recommended_candidate_color);
        mNormalCandidateColor = mImeCandidateColor;
        mActiveCandidateColor = r.getColor(R.color.active_candidate_color);

        mCandidatesPaint = new Paint();
        mCandidatesPaint.setAntiAlias(true);

        mFootnotePaint = new Paint();
        mFootnotePaint.setAntiAlias(true);
        mFootnotePaint.setColor(r.getColor(R.color.footnote_color));
        mActiveCellRect = new RectF();

        mCandRects = new Vector<RectF>();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mOldWidth = getMeasuredWidth();
        int mOldHeight = getMeasuredHeight();

        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(),
                widthMeasureSpec), getDefaultSize(getSuggestedMinimumHeight(),
                heightMeasureSpec));

        if (mOldWidth != getMeasuredWidth() || mOldHeight != getMeasuredHeight()) {
            onSizeChanged();
        }
    }

    public void initialize(ArrowUpdater arrowUpdater, BalloonHint balloonHint,
            GestureDetector gestureDetector, CandidateViewListener cvListener) {
        mArrowUpdater = arrowUpdater;
        mBalloonHint = balloonHint;
        mGestureDetector = gestureDetector;
        mCvListener = cvListener;
    }

    public void setDecodingInfo(DecodingInfo decInfo) {
        if (null == decInfo) return;
        mDecInfo = decInfo;
        mPageNoCalculated = -1;

        if (mDecInfo.candidatesFromApp()) {
            mNormalCandidateColor = mRecommendedCandidateColor;
            mCandidateTextSize = mRecommendedCandidateTextSize;
        } else {
            mNormalCandidateColor = mImeCandidateColor;
            mCandidateTextSize = mImeCandidateTextSize;
        }
        if (mCandidatesPaint.getTextSize() != mCandidateTextSize) {
            mCandidatesPaint.setTextSize(mCandidateTextSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
            mSuspensionPointsWidth =
                    mCandidatesPaint.measureText(SUSPENSION_POINTS);
        }

        // Remove any pending timer for the previous list.
        mTimer.removeTimer();
    }

    public int getActiveCandiatePosInPage() {
        return mActiveCandInPage;
    }

    public int getActiveCandiatePosGlobal() {
        return mDecInfo.mPageStart.get(mPageNo) + mActiveCandInPage;
    }

    /**
     * Show a page in the decoding result set previously.
     *
     * @param pageNo Which page to show.
     * @param activeCandInPage Which candidate should be set as active item.
     * @param enableActiveHighlight When false, active item will not be
     *        highlighted.
     */
    public void showPage(int pageNo, int activeCandInPage,
            boolean enableActiveHighlight) {
        if (null == mDecInfo) return;
        mPageNo = pageNo;
        mActiveCandInPage = activeCandInPage;
        if (mEnableActiveHighlight != enableActiveHighlight) {
            mEnableActiveHighlight = enableActiveHighlight;
        }

        if (!calculatePage(mPageNo)) {
            mUpdateArrowStatusWhenDraw = true;
        } else {
            mUpdateArrowStatusWhenDraw = false;
        }

        invalidate();
    }

    public void enableActiveHighlight(boolean enableActiveHighlight) {
        if (enableActiveHighlight == mEnableActiveHighlight) return;

        mEnableActiveHighlight = enableActiveHighlight;
        invalidate();
    }

    public boolean activeCursorForward() {
        if (!mDecInfo.pageReady(mPageNo)) return false;
        int pageSize = mDecInfo.mPageStart.get(mPageNo + 1)
                - mDecInfo.mPageStart.get(mPageNo);
        if (mActiveCandInPage + 1 < pageSize) {
            showPage(mPageNo, mActiveCandInPage + 1, true);
            return true;
        }
        return false;
    }

    public boolean activeCurseBackward() {
        if (mActiveCandInPage > 0) {
            showPage(mPageNo, mActiveCandInPage - 1, true);
            return true;
        }
        return false;
    }

    private void onSizeChanged() {
        mContentWidth = getMeasuredWidth() - mPaddingLeft - mPaddingRight;
        mContentHeight = (int) ((getMeasuredHeight() - mPaddingTop - mPaddingBottom) * 0.95f);
        /**
         * How to decide the font size if the height for display is given?
         * Now it is implemented in a stupid way.
         */
        int textSize = 1;
        mCandidatesPaint.setTextSize(textSize);
        mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
        while (mFmiCandidates.bottom - mFmiCandidates.top < mContentHeight) {
            textSize++;
            mCandidatesPaint.setTextSize(textSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
        }

        mImeCandidateTextSize = textSize;
        mRecommendedCandidateTextSize = textSize * 3 / 4;
        if (null == mDecInfo) {
            mCandidateTextSize = mImeCandidateTextSize;
            mCandidatesPaint.setTextSize(mCandidateTextSize);
            mFmiCandidates = mCandidatesPaint.getFontMetricsInt();
            mSuspensionPointsWidth =
                mCandidatesPaint.measureText(SUSPENSION_POINTS);
        } else {
            // Reset the decoding information to update members for painting.
            setDecodingInfo(mDecInfo);
        }

        textSize = 1;
        mFootnotePaint.setTextSize(textSize);
        mFmiFootnote = mFootnotePaint.getFontMetricsInt();
        while (mFmiFootnote.bottom - mFmiFootnote.top < mContentHeight / 2) {
            textSize++;
            mFootnotePaint.setTextSize(textSize);
            mFmiFootnote = mFootnotePaint.getFontMetricsInt();
        }
        textSize--;
        mFootnotePaint.setTextSize(textSize);
        mFmiFootnote = mFootnotePaint.getFontMetricsInt();
    }

    private boolean calculatePage(int pageNo) {
        if (pageNo == mPageNoCalculated) return true;

        mContentWidth = getMeasuredWidth() - mPaddingLeft - mPaddingRight;
        mContentHeight = (int) ((getMeasuredHeight() - mPaddingTop - mPaddingBottom) * 0.95f);

        if (mContentWidth <= 0 || mContentHeight <= 0) return false;

        int candSize = mDecInfo.mCandidatesList.size();

        // If the size of page exists, only calculate the extra margin.
        boolean onlyExtraMargin = false;
        int fromPage = mDecInfo.mPageStart.size() - 1;
        if (mDecInfo.mPageStart.size() > pageNo + 1) {
            onlyExtraMargin = true;
            fromPage = pageNo;
        }

        // If the previous pages have no information, calculate them first.
        for (int p = fromPage; p <= pageNo; p++) {
            int pStart = mDecInfo.mPageStart.get(p);
            int pSize = 0;
            int charNum = 0;
            float lastItemWidth = 0;

            float xPos;
            xPos = 0;
            xPos += mSeparatorDrawable.getIntrinsicWidth();
            while (xPos < mContentWidth && pStart + pSize < candSize) {
                int itemPos = pStart + pSize;
                String itemStr = mDecInfo.mCandidatesList.get(itemPos);
                float itemWidth = mCandidatesPaint.measureText(itemStr);
                if (itemWidth < MIN_ITEM_WIDTH) itemWidth = MIN_ITEM_WIDTH;

                itemWidth += mCandidateMargin * 2;
                itemWidth += mSeparatorDrawable.getIntrinsicWidth();
                if (xPos + itemWidth < mContentWidth || 0 == pSize) {
                    xPos += itemWidth;
                    lastItemWidth = itemWidth;
                    pSize++;
                    charNum += itemStr.length();
                } else {
                    break;
                }
            }
            if (!onlyExtraMargin) {
                mDecInfo.mPageStart.add(pStart + pSize);
                mDecInfo.mCnToPage.add(mDecInfo.mCnToPage.get(p) + charNum);
            }

            float marginExtra = (mContentWidth - xPos) / pSize / 2;

            if (mContentWidth - xPos > lastItemWidth) {
                // Must be the last page, because if there are more items,
                // the next item's width must be less than lastItemWidth.
                // In this case, if the last margin is less than the current
                // one, the last margin can be used, so that the
                // look-and-feeling will be the same as the previous page.
                if (mCandidateMarginExtra <= marginExtra) {
                    marginExtra = mCandidateMarginExtra;
                }
            } else if (pSize == 1) {
                marginExtra = 0;
            }
            mCandidateMarginExtra = marginExtra;
        }
        mPageNoCalculated = pageNo;
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // The invisible candidate view(the one which is not in foreground) can
        // also be called to drawn, but its decoding result and candidate list
        // may be empty.
        if (null == mDecInfo || mDecInfo.isCandidatesListEmpty()) return;

        // Calculate page. If the paging information is ready, the function will
        // return at once.
        calculatePage(mPageNo);

        int pStart = mDecInfo.mPageStart.get(mPageNo);
        int pSize = mDecInfo.mPageStart.get(mPageNo + 1) - pStart;
        float candMargin = mCandidateMargin + mCandidateMarginExtra;
        if (mActiveCandInPage > pSize - 1) {
            mActiveCandInPage = pSize - 1;
        }

        mCandRects.removeAllElements();

        float xPos = mPaddingLeft;
        int yPos = (getMeasuredHeight() -
                (mFmiCandidates.bottom - mFmiCandidates.top)) / 2
                - mFmiCandidates.top;
        xPos += drawVerticalSeparator(canvas, xPos);
        for (int i = 0; i < pSize; i++) {
            float footnoteSize = 0;
            String footnote = null;
            if (mShowFootnote) {
                footnote = Integer.toString(i + 1);
                footnoteSize = mFootnotePaint.measureText(footnote);
                assert (footnoteSize < candMargin);
            }
            String cand = mDecInfo.mCandidatesList.get(pStart + i);
            float candidateWidth = mCandidatesPaint.measureText(cand);
            float centerOffset = 0;
            if (candidateWidth < MIN_ITEM_WIDTH) {
                centerOffset = (MIN_ITEM_WIDTH - candidateWidth) / 2;
                candidateWidth = MIN_ITEM_WIDTH;
            }

            float itemTotalWidth = candidateWidth + 2 * candMargin;

            if (mActiveCandInPage == i && mEnableActiveHighlight) {
                mActiveCellRect.set(xPos, mPaddingTop + 1, xPos
                        + itemTotalWidth, getHeight() - mPaddingBottom - 1);
                mActiveCellDrawable.setBounds((int) mActiveCellRect.left,
                        (int) mActiveCellRect.top, (int) mActiveCellRect.right,
                        (int) mActiveCellRect.bottom);
                mActiveCellDrawable.draw(canvas);
            }

            if (mCandRects.size() < pSize) mCandRects.add(new RectF());
            mCandRects.elementAt(i).set(xPos - 1, yPos + mFmiCandidates.top,
                    xPos + itemTotalWidth + 1, yPos + mFmiCandidates.bottom);

            // Draw footnote
            if (mShowFootnote) {
                canvas.drawText(footnote, xPos + (candMargin - footnoteSize)
                        / 2, yPos, mFootnotePaint);
            }

            // Left margin
            xPos += candMargin;
            if (candidateWidth > mContentWidth - xPos - centerOffset) {
                cand = getLimitedCandidateForDrawing(cand,
                        mContentWidth - xPos - centerOffset);
            }
            if (mActiveCandInPage == i && mEnableActiveHighlight) {
                mCandidatesPaint.setColor(mActiveCandidateColor);
            } else {
                mCandidatesPaint.setColor(mNormalCandidateColor);
            }
            canvas.drawText(cand, xPos + centerOffset, yPos,
                    mCandidatesPaint);

            // Candidate and right margin
            xPos += candidateWidth + candMargin;

            // Draw the separator between candidates.
            xPos += drawVerticalSeparator(canvas, xPos);
        }

        // Update the arrow status of the container.
        if (null != mArrowUpdater && mUpdateArrowStatusWhenDraw) {
            mArrowUpdater.updateArrowStatus();
            mUpdateArrowStatusWhenDraw = false;
        }
    }

    private String getLimitedCandidateForDrawing(String rawCandidate,
            float widthToDraw) {
        int subLen = rawCandidate.length();
        if (subLen <= 1) return rawCandidate;
        do {
            subLen--;
            float width = mCandidatesPaint.measureText(rawCandidate, 0, subLen);
            if (width + mSuspensionPointsWidth <= widthToDraw || 1 >= subLen) {
                return rawCandidate.substring(0, subLen) +
                        SUSPENSION_POINTS;
            }
        } while (true);
    }

    private float drawVerticalSeparator(Canvas canvas, float xPos) {
        mSeparatorDrawable.setBounds((int) xPos, mPaddingTop, (int) xPos
                + mSeparatorDrawable.getIntrinsicWidth(), getMeasuredHeight()
                - mPaddingBottom);
        mSeparatorDrawable.draw(canvas);
        return mSeparatorDrawable.getIntrinsicWidth();
    }

    private int mapToItemInPage(int x, int y) {
        // mCandRects.size() == 0 happens when the page is set, but
        // touch events occur before onDraw(). It usually happens with
        // monkey test.
        if (!mDecInfo.pageReady(mPageNo) || mPageNoCalculated != mPageNo
                || mCandRects.size() == 0) {
            return -1;
        }

        int pageStart = mDecInfo.mPageStart.get(mPageNo);
        int pageSize = mDecInfo.mPageStart.get(mPageNo + 1) - pageStart;
        if (mCandRects.size() < pageSize) {
            return -1;
        }

        // If not found, try to find the nearest one.
        float nearestDis = Float.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < pageSize; i++) {
            RectF r = mCandRects.elementAt(i);
            if (r.left < x && r.right > x && r.top < y && r.bottom > y) {
                return i;
            }
            float disx = (r.left + r.right) / 2 - x;
            float disy = (r.top + r.bottom) / 2 - y;
            float dis = disx * disx + disy * disy;
            if (dis < nearestDis) {
                nearestDis = dis;
                nearest = i;
            }
        }

        return nearest;
    }

    // Because the candidate view under the current focused one may also get
    // touching events. Here we just bypass the event to the container and let
    // it decide which view should handle the event.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public boolean onTouchEventReal(MotionEvent event) {
        // The page in the background can also be touched.
        if (null == mDecInfo || !mDecInfo.pageReady(mPageNo)
                || mPageNoCalculated != mPageNo) return true;

        int x, y;
        x = (int) event.getX();
        y = (int) event.getY();

        if (mGestureDetector.onTouchEvent(event)) {
            mTimer.removeTimer();
            mBalloonHint.delayedDismiss(0);
            return true;
        }

        int clickedItemInPage = -1;

        switch (event.getAction()) {
        case MotionEvent.ACTION_UP:
            clickedItemInPage = mapToItemInPage(x, y);
            if (clickedItemInPage >= 0) {
                invalidate();
                mCvListener.onClickChoice(clickedItemInPage
                        + mDecInfo.mPageStart.get(mPageNo));
            }
            mBalloonHint.delayedDismiss(BalloonHint.TIME_DELAY_DISMISS);
            break;

        case MotionEvent.ACTION_DOWN:
            clickedItemInPage = mapToItemInPage(x, y);
            if (clickedItemInPage >= 0) {
                showBalloon(clickedItemInPage, true);
                mTimer.startTimer(BalloonHint.TIME_DELAY_SHOW, mPageNo,
                        clickedItemInPage);
            }
            break;

        case MotionEvent.ACTION_CANCEL:
            break;

        case MotionEvent.ACTION_MOVE:
            clickedItemInPage = mapToItemInPage(x, y);
            if (clickedItemInPage >= 0
                    && (clickedItemInPage != mTimer.getActiveCandOfPageToShow() || mPageNo != mTimer
                            .getPageToShow())) {
                showBalloon(clickedItemInPage, true);
                mTimer.startTimer(BalloonHint.TIME_DELAY_SHOW, mPageNo,
                        clickedItemInPage);
            }
        }
        return true;
    }

    private void showBalloon(int candPos, boolean delayedShow) {
        mBalloonHint.removeTimer();

        RectF r = mCandRects.elementAt(candPos);
        int desired_width = (int) (r.right - r.left);
        int desired_height = (int) (r.bottom - r.top);
        mBalloonHint.setBalloonConfig(mDecInfo.mCandidatesList
                .get(mDecInfo.mPageStart.get(mPageNo) + candPos), 44, true,
                mImeCandidateColor, desired_width, desired_height);

        getLocationOnScreen(mLocationTmp);
        mHintPositionToInputView[0] = mLocationTmp[0]
                + (int) (r.left - (mBalloonHint.getWidth() - desired_width) / 2);
        mHintPositionToInputView[1] = -mBalloonHint.getHeight();

        long delay = BalloonHint.TIME_DELAY_SHOW;
        if (!delayedShow) delay = 0;
        mBalloonHint.dismiss();
        if (!mBalloonHint.isShowing()) {
            mBalloonHint.delayedShow(delay, mHintPositionToInputView);
        } else {
            mBalloonHint.delayedUpdate(0, mHintPositionToInputView, -1, -1);
        }
    }

    private class PressTimer extends Handler implements Runnable {
        private boolean mTimerPending = false;
        private int mPageNoToShow;
        private int mActiveCandOfPage;

        public PressTimer() {
            super();
        }

        public void startTimer(long afterMillis, int pageNo, int activeInPage) {
            mTimer.removeTimer();
            postDelayed(this, afterMillis);
            mTimerPending = true;
            mPageNoToShow = pageNo;
            mActiveCandOfPage = activeInPage;
        }

        public int getPageToShow() {
            return mPageNoToShow;
        }

        public int getActiveCandOfPageToShow() {
            return mActiveCandOfPage;
        }

        public boolean removeTimer() {
            if (mTimerPending) {
                mTimerPending = false;
                removeCallbacks(this);
                return true;
            }
            return false;
        }

        public boolean isPending() {
            return mTimerPending;
        }

        public void run() {
            if (mPageNoToShow >= 0 && mActiveCandOfPage >= 0) {
                // Always enable to highlight the clicked one.
                showPage(mPageNoToShow, mActiveCandOfPage, true);
                invalidate();
            }
            mTimerPending = false;
        }
    }
}