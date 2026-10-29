package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** The parent provides bounds from the actual window; no physical-screen height assumption. */
public final class ControlScrollView extends ScrollView {
    public ControlScrollView(Context context, AttributeSet attrs) { super(context, attrs); }
}
