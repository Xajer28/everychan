package com.nttec.everychan.lib.gallery;

import com.nttec.everychan.common.Logger;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ViewPagerFixed extends ViewPager {
    public ViewPagerFixed(Context context) {
        super(context);
    }
    
    public ViewPagerFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    /**
     * Hacky fix for Issue #4 and
     * http://code.google.com/p/android/issues/detail?id=18990
     * <p/>
     * ScaleGestureDetector seems to mess up the touch events, which means that
     * ViewGroups which make use of onInterceptTouchEvent throw a lot of
     * IllegalArgumentException: pointerIndex out of range.
     * <p/>
     * There's not much I can do in my code for now, but we can mask the result by
     * just catching the problem and ignoring it.
     *
     * @author Chris Banes
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (Exception e) {
            Logger.e("ViewPager", e);
            return false;
        }
    }
    
    /**
     * Корректный скроллинг на ранних версиях Android
     */
    @Override
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (v instanceof FixedSubsamplingScaleImageView) {
                return ((FixedSubsamplingScaleImageView) v).canScrollHorizontallyOldAPI(-dx);
            } else if (v instanceof WebViewFixed) {
                return ((WebViewFixed) v).canScrollHorizontallyOldAPI(-dx);
            } else if (v instanceof TouchGifView) {
                return ((TouchGifView) v).canScrollHorizontallyOldAPI(-dx); 
            }
        }
        return super.canScroll(v, checkV, dx, x, y);
    }
}