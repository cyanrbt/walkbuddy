package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Reserves camera space; the complete settings panel scrolls inside the remaining bounds. */
public final class PreviewContentLayout extends ViewGroup {
    private boolean sideBySide;

    public PreviewContentLayout(Context context,AttributeSet attrs) {super(context,attrs);}

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        float dp=getResources().getDisplayMetrics().density;
        View camera=getChildAt(0),panel=getChildAt(1);
        sideBySide=width>=600*dp && width>height;
        int panelWidth=width,panelHeight=0;
        if(panel.getVisibility()!=GONE) {
            if(sideBySide) {
                panelWidth=Math.min(width,Math.round(Math.max(240*dp,Math.min(360*dp,width*.4f))));
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
}
