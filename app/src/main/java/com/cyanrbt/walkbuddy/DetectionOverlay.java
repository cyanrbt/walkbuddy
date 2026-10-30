package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import java.util.*;

/** Geometry and labels are prepared only for new results or a changed preview transform. */
public final class DetectionOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.text.TextPaint textPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final DashPathEffect cautionDash;
    private List<Detection> detections=Collections.emptyList();
    private PreviewGeometry geometry;
    private Entry[] entries=new Entry[0];

    private static final class Entry {
        final Path edges=new Path();
        Path fill;
        int color;
        float stroke, labelX, labelY, labelWidth;
        boolean caution;
        String label;
    }

    public DetectionOverlay(Context context,AttributeSet attrs) {
        super(context,attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        dp=getResources().getDisplayMetrics().density;
        cautionDash=new DashPathEffect(new float[]{6*dp,4*dp},0);
        textPaint.setTextSize(11*dp);
        textPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Inputs are immutable snapshots; repeated expiry checks can reuse the same drawing. */
    void show(List<Detection> detections,PreviewGeometry geometry) {
        if(this.detections==detections && this.geometry==geometry)return;
        this.detections=detections;this.geometry=geometry;
        entries=geometry==null?new Entry[0]:prepare(detections,geometry);
        invalidate();
    }

    void clear() {
        if(detections.isEmpty())return;
        detections=Collections.emptyList();entries=new Entry[0];invalidate();
    }

    private Entry[] prepare(List<Detection> detections,PreviewGeometry g) {
        Entry[] out=new Entry[detections.size()];
        for(int i=0;i<out.length;i++) {
            Detection d=detections.get(i);Entry e=new Entry();out[i]=e;
            float l=g.left+d.left*g.width,t=g.top+d.top*g.height,r=g.left+d.right*g.width,b=g.top+d.bottom*g.height;
            e.color=d.risk==Detection.Risk.NEAR?Color.rgb(255,99,110):d.risk==Detection.Risk.CAUTION?Color.rgb(255,200,87):Color.rgb(174,183,197);
            e.caution=d.risk==Detection.Risk.CAUTION;
            e.stroke=(d.risk==Detection.Risk.NEAR?2.5f:1.5f)*dp;
            if(d.mask!=null) {
                e.fill=new Path();
                MaskGeometry mask=new MaskGeometry(d.mask);
                float sx=g.width/d.mask.width,sy=g.height/d.mask.height;
                for(MaskGeometry.Run fill:mask.fills)e.fill.addRect(g.left+fill.left*sx,g.top+fill.top*sy,
                    g.left+fill.right*sx,g.top+fill.bottom*sy,Path.Direction.CW);
                for(MaskGeometry.Run edge:mask.edges) {
                    e.edges.moveTo(g.left+edge.left*sx,g.top+edge.top*sy);
                    e.edges.lineTo(g.left+edge.right*sx,g.top+edge.bottom*sy);
                }
            } else {
                float corner=Math.min(18*dp,Math.min(r-l,b-t)/3);
                e.edges.moveTo(l,t+corner);e.edges.lineTo(l,t);e.edges.lineTo(l+corner,t);
                e.edges.moveTo(r-corner,t);e.edges.lineTo(r,t);e.edges.lineTo(r,t+corner);
                e.edges.moveTo(l,b-corner);e.edges.lineTo(l,b);e.edges.lineTo(l+corner,b);
                e.edges.moveTo(r-corner,b);e.edges.lineTo(r,b);e.edges.lineTo(r,b-corner);
            }
            String label=Float.isFinite(d.meters)?String.format(Locale.CHINA,"约 %.1f 米 · %s",d.meters,d.label)
                :d.attention()?"未测距 · "+d.label:d.label;
            e.label=TextUtils.ellipsize(label,textPaint,Math.max(0,g.width-12*dp),TextUtils.TruncateAt.END).toString();
            e.labelWidth=textPaint.measureText(e.label);
            e.labelX=Math.max(g.left+2*dp,Math.min(l,g.left+g.width-e.labelWidth-12*dp));
            e.labelY=Math.max(g.top+19*dp,Math.min(t-5*dp,g.top+g.height-5*dp));
        }
        return out;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(geometry==null || entries.length==0)return;
        canvas.save();canvas.clipRect(geometry.left,geometry.top,geometry.left+geometry.width,geometry.top+geometry.height);
        for(int i=0;i<entries.length;i++) {
            Entry e=entries[i];
            if(e.fill!=null) {
                paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);
                paint.setColor((e.color&0xFFFFFF)|0x24000000);canvas.drawPath(e.fill,paint);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(e.stroke);paint.setColor(e.color);
            // Restore the dash after the translucent fill, for depth masks as well as RGB boxes.
            paint.setPathEffect(e.caution?cautionDash:null);canvas.drawPath(e.edges,paint);
            if(!e.label.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);paint.setColor(Color.argb(215,18,23,32));
                canvas.drawRoundRect(e.labelX,e.labelY-16*dp,e.labelX+e.labelWidth+10*dp,e.labelY+5*dp,6*dp,6*dp,paint);
                textPaint.setColor(e.color);canvas.drawText(e.label,e.labelX+5*dp,e.labelY,textPaint);
            }
        }
        canvas.restore();
    }
}
