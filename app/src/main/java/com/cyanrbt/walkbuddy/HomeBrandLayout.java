package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Decorative artwork yields space to the wordmark at the user's chosen text size. */
public final class HomeBrandLayout extends LinearLayout {
    public HomeBrandLayout(Context context,AttributeSet attributes){super(context,attributes);}

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        TextView name=findViewById(R.id.home_brand_name);
        View art=findViewById(R.id.home_brand_art);
        LayoutParams decoration=(LayoutParams)art.getLayoutParams();
        int available=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE
            :MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        float required=Layout.getDesiredWidth(name.getText(),name.getPaint())
            +name.getCompoundPaddingLeft()+name.getCompoundPaddingRight()
            +decoration.width+decoration.getMarginStart()+decoration.getMarginEnd();
        int visibility=required<=available?VISIBLE:GONE;
        if(art.getVisibility()!=visibility)art.setVisibility(visibility);
        super.onMeasure(widthSpec,heightSpec);
    }
}
