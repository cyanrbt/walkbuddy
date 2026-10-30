package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

/** Retains the XML preview style while giving stopped compact windows readable, short content. */
public final class PreviewCover extends TextView {
    private final float previewSize,previewSpacing,previewMultiplier;
    private final int previewLeft,previewTop,previewRight,previewBottom;
    private int presentation;

    public PreviewCover(Context context,AttributeSet attrs) {
        super(context,attrs);
        previewSize=getTextSize();previewSpacing=getLineSpacingExtra();previewMultiplier=getLineSpacingMultiplier();
        previewLeft=getPaddingLeft();previewTop=getPaddingTop();previewRight=getPaddingRight();previewBottom=getPaddingBottom();
    }
    void recovery(boolean compact) {presentation(compact?2:1);}
    void preview(){presentation(0);}
    private void presentation(int next) {
        if(presentation==next)return;
        presentation=next;
        if(next==0) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,previewSize);
            setPadding(previewLeft,previewTop,previewRight,previewBottom);
            setLineSpacing(previewSpacing,previewMultiplier);
        } else {
            if(next==2)setTextSize(TypedValue.COMPLEX_UNIT_SP,12);
            else setTextSize(TypedValue.COMPLEX_UNIT_PX,previewSize);
            int padding=Math.round((next==2?8:16)*getResources().getDisplayMetrics().density);
            setPadding(padding,padding,padding,padding);
            setLineSpacing(next==2?0:previewSpacing,previewMultiplier);
        }
    }
}
