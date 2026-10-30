package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keep a full risk label and its tactile cue readable at the user's chosen text size. */
public final class RiskLegendLayout extends LinearLayout {
    public RiskLegendLayout(Context context,AttributeSet attrs){super(context,attrs);}

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int available=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        float widest=0;
        for(int i=0;i<getChildCount();i++) {
            TextView text=(TextView)getChildAt(i);
            for(String line:text.getText().toString().split("\n"))
                widest=Math.max(widest,text.getPaint().measureText(line)+text.getCompoundPaddingLeft()+text.getCompoundPaddingRight());
        }
        int orientation=MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED
            && Math.ceil(widest)*getChildCount()<=available?HORIZONTAL:VERTICAL;
        if(orientation!=getOrientation()) {
            setOrientation(orientation);
            for(int i=0;i<getChildCount();i++) {
                TextView text=(TextView)getChildAt(i);
                LayoutParams params=(LayoutParams)text.getLayoutParams();
                params.width=orientation==HORIZONTAL?0:LayoutParams.MATCH_PARENT;
                params.weight=orientation==HORIZONTAL?1:0;
                params.bottomMargin=orientation==VERTICAL && i+1<getChildCount()
                    ?Math.round(8*getResources().getDisplayMetrics().density):0;
                text.setGravity(orientation==HORIZONTAL?Gravity.CENTER_HORIZONTAL:Gravity.START);
                text.setLayoutParams(params);
            }
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
