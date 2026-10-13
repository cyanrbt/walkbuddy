package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** Keeps camera space available on short screens while controls remain scrollable. */
public final class ControlScrollView extends ScrollView {
    public ControlScrollView(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        // Configuration dimensions update before measurement after rotation or multi-window resize.
        int available = Math.round(getResources().getConfiguration().screenHeightDp
            * getResources().getDisplayMetrics().density);
        int cap = Math.max(1, (int) (available * 0.42f));
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            cap = Math.min(cap, MeasureSpec.getSize(heightSpec));
        }
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
    }
}
