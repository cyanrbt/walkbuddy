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
    private final float textAbove,textBelow;
    private final Rect glyphBounds=new Rect();
    private final DashPathEffect cautionDash;
    private List<Detection> detections=Collections.emptyList();
    private PreviewGeometry geometry;
    private Entry[] entries=new Entry[0];
    private Entry[] drawingEntries=entries;

    private static final class Entry {
        final Path edges=new Path();
        Path fill;
        int color,priority;
        float stroke, labelX, labelY, labelWidth, labelTop, labelBottom;
        float anchorX,anchorY,leaderX,leaderY;
        boolean caution;
        String label;
    }

    public DetectionOverlay(Context context,AttributeSet attrs) {
        super(context,attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        dp=getResources().getDisplayMetrics().density;
        cautionDash=new DashPathEffect(new float[]{6*dp,4*dp},0);
        // Let Resources apply platform sp conversion, including nonlinear font scaling.
        textPaint.setTextSize(getResources().getDimension(R.dimen.detection_label_text_size));
        textPaint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        Paint.FontMetrics metrics=textPaint.getFontMetrics();
        textAbove=-metrics.top;textBelow=metrics.bottom;
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Inputs are immutable snapshots; repeated expiry checks can reuse the same drawing. */
    void show(List<Detection> detections,PreviewGeometry geometry) {
        if(this.detections==detections && this.geometry==geometry)return;
        this.detections=detections;this.geometry=geometry;
        entries=geometry==null?new Entry[0]:prepare(detections,geometry);
        drawingEntries=entries.clone();
        // Low-risk shapes first; a later low-risk result must not repaint a near outline.
        Arrays.sort(drawingEntries,Comparator.comparingInt(entry->entry.priority));
        invalidate();
    }

    void clear() {
        if(detections.isEmpty())return;
        detections=Collections.emptyList();entries=drawingEntries=new Entry[0];invalidate();
    }

    private Entry[] prepare(List<Detection> detections,PreviewGeometry g) {
        Entry[] out=new Entry[detections.size()];
        OverlayLabelLayout.Label[] labels=new OverlayLabelLayout.Label[out.length];
        for(int i=0;i<out.length;i++) {
            Detection d=detections.get(i);Entry e=new Entry();out[i]=e;
            e.priority=d.risk.ordinal();
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
            e.label=fitLabel(d,Math.max(0,g.width-12*dp));
            e.labelWidth=textPaint.measureText(e.label);
            textPaint.getTextBounds(e.label,0,e.label.length(),glyphBounds);
            float above=Math.max(textAbove,-glyphBounds.top),below=Math.max(textBelow,glyphBounds.bottom);
            float height=above+below+8*dp;
            if(height+4*dp>g.height){e.label="";e.labelWidth=0;}
            e.labelY=4*dp+above;
            if(!e.label.isEmpty())labels[i]=new OverlayLabelLayout.Label(d,l,t,r,b,e.labelWidth+8*dp,height);
        }
        OverlayLabelLayout.Position[] positions=OverlayLabelLayout.place(labels,g.left,g.top,g.width,g.height,dp);
        for(int i=0;i<out.length;i++) {
            Entry e=out[i];OverlayLabelLayout.Position position=positions[i];
            if(position==null){e.label="";e.labelWidth=0;continue;}
            OverlayLabelLayout.Label size=labels[i];
            e.labelX=position.left;e.labelTop=position.top;e.labelBottom=position.top+size.height;e.labelY+=position.top;
            e.anchorX=Math.max(size.left,Math.min(size.right,e.labelX+size.width/2));
            e.anchorY=position.bottomAnchor?size.bottom:size.top;
            e.leaderX=Math.max(e.labelX+4*dp,Math.min(e.anchorX,e.labelX+size.width-4*dp));
            e.leaderY=Math.max(e.labelTop,Math.min(e.anchorY,e.labelBottom));
        }
        return out;
    }

    private String fitLabel(Detection detection,float width) {
        String qualifier=Float.isFinite(detection.meters)?String.format(Locale.CHINA,"约 %.1f 米",detection.meters)
            :detection.attention()?"未测距":null;
        String prefix=qualifier==null?"":qualifier+" · ";
        String full=prefix+detection.label;
        String fitted=TextUtils.ellipsize(full,textPaint,width,TextUtils.TruncateAt.END).toString();
        boolean hasName=fitted.startsWith(prefix) && !fitted.substring(prefix.length()).replace("…","").trim().isEmpty();
        if(qualifier!=null && !hasName) {
            if(textPaint.measureText(qualifier)<=width)return qualifier;
            String compact=qualifier.replace(" ","");
            return textPaint.measureText(compact)<=width?compact:"";
        }
        return "…".equals(fitted)?"":fitted;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(geometry==null || entries.length==0)return;
        canvas.save();canvas.clipRect(geometry.left,geometry.top,geometry.left+geometry.width,geometry.top+geometry.height);
        // Draw every shape before any text, so later masks or borders cannot tint a label.
        for(Entry e:drawingEntries) {
            if(e.fill!=null) {
                paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);
                paint.setColor((e.color&0xFFFFFF)|0x24000000);canvas.drawPath(e.fill,paint);
            }
        }
        for(Entry e:drawingEntries) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(e.stroke);paint.setColor(e.color);
            // Restore the dash after the translucent fill, for depth masks as well as RGB boxes.
            paint.setPathEffect(e.caution?cautionDash:null);canvas.drawPath(e.edges,paint);
        }
        paint.setPathEffect(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp);
        for(Entry e:drawingEntries)if(!e.label.isEmpty()) {
            paint.setColor(e.color);canvas.drawLine(e.anchorX,e.anchorY,e.leaderX,e.leaderY,paint);
        }
        for(Entry e:drawingEntries) {
            if(!e.label.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);paint.setColor(Color.argb(235,18,23,32));
                canvas.drawRoundRect(e.labelX,e.labelTop,e.labelX+e.labelWidth+8*dp,e.labelBottom,6*dp,6*dp,paint);
                textPaint.setColor(e.color);canvas.drawText(e.label,e.labelX+4*dp,e.labelY,textPaint);
            }
        }
        canvas.restore();
    }
}
