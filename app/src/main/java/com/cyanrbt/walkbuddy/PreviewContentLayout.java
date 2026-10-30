package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.text.Layout;

/** Reserves camera space; the complete settings panel scrolls inside the remaining bounds. */
public final class PreviewContentLayout extends ViewGroup {
    private boolean sideBySide,recovering;

    public PreviewContentLayout(Context context,AttributeSet attrs) {super(context,attrs);}
    void setRecovering(boolean value) {
        if(recovering==value)return;
        recovering=value;
        if(value)getChildAt(1).scrollTo(0,0);
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        float dp=getResources().getDisplayMetrics().density;
        View camera=getChildAt(0),panel=getChildAt(1);
        if(recovering && panel.getVisibility()!=GONE) {
            sideBySide=false;
            View cover=camera.findViewById(R.id.preview_cover);
            cover.measure(exact(width),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int titleHeight=Math.min(height,cover.getMeasuredHeight());
            camera.measure(exact(width),exact(titleHeight));
            panel.measure(exact(width),exact(height-titleHeight));
            setMeasuredDimension(width,height);return;
        }
        sideBySide=width>=600*dp && width>height;
        int panelWidth=width,panelHeight=0;
        if(panel.getVisibility()!=GONE) {
            if(sideBySide) {
                panelWidth=Math.min(width,Math.round(Math.max(240*dp,Math.min(360*dp,width*.4f))));
                // Equal-weight switches need two cells wide enough for the longer full label.
                panelWidth=Math.min(Math.round(width*.6f),Math.max(panelWidth,toggleWidth(panel)));
                panel.measure(exact(panelWidth),exact(height));
                panelHeight=height;
            } else {
                // At least half remains for the camera, even when text grows or the window shrinks.
                int cap=Math.round(Math.max(height*.4f,Math.min(height*.5f,96*dp)));
                panel.measure(exact(width),MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
                panelHeight=panel.getMeasuredHeight();
            }
        } else panelWidth=0;
        camera.measure(exact(sideBySide?width-panelWidth:width),exact(sideBySide?height:height-panelHeight));
        setMeasuredDimension(width,height);
    }

    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        View camera=getChildAt(0),panel=getChildAt(1);
        int w=camera.getMeasuredWidth(),h=camera.getMeasuredHeight();
        camera.layout(0,0,w,h);
        if(panel.getVisibility()!=GONE) {
            if(sideBySide)panel.layout(w,0,getWidth(),getHeight());
            else panel.layout(0,h,getWidth(),getHeight());
        }
    }

    private static int exact(int size) {return MeasureSpec.makeMeasureSpec(Math.max(0,size),MeasureSpec.EXACTLY);}
    private static int toggleWidth(View panel) {
        TextView detection=panel.findViewById(R.id.preview_recognition),sound=panel.findViewById(R.id.preview_sound);
        if(detection==null || sound==null)return 0;
        int width=2*Math.max(labelWidth(detection),labelWidth(sound))+margins(detection)+margins(sound);
        for(View view=(View)detection.getParent();view!=panel;) {
            width+=view.getPaddingLeft()+view.getPaddingRight()+margins(view);
            if(!(view.getParent() instanceof View))break;
            view=(View)view.getParent();
        }
        return width+panel.getPaddingLeft()+panel.getPaddingRight();
    }
    private static int labelWidth(TextView text) {
        return Math.max(text.getMinimumWidth(),(int)Math.ceil(Layout.getDesiredWidth(text.getText(),text.getPaint()))
            +text.getCompoundPaddingLeft()+text.getCompoundPaddingRight());
    }
    private static int margins(View view) {
        if(!(view.getLayoutParams() instanceof MarginLayoutParams))return 0;
        MarginLayoutParams params=(MarginLayoutParams)view.getLayoutParams();
        return params.getMarginStart()+params.getMarginEnd();
    }
}
