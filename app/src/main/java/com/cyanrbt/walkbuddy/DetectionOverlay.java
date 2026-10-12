package com.cyanrbt.walkbuddy;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.*;

/** Masks follow measured depth occupancy; RGB detections retain honest corner brackets. */
public final class DetectionOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Detection> detections=Collections.emptyList();private PreviewGeometry geometry;
    public DetectionOverlay(Context context,AttributeSet attrs){super(context,attrs);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void show(List<Detection> detections,PreviewGeometry geometry){this.detections=detections;this.geometry=geometry;invalidate();}
    void clear(){if(detections.isEmpty())return;detections=Collections.emptyList();invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);if(geometry==null)return;float dp=getResources().getDisplayMetrics().density;
        canvas.save();canvas.clipRect(geometry.left,geometry.top,geometry.left+geometry.width,geometry.top+geometry.height);
        for(Detection d:detections) {
            float l=geometry.left+d.left*geometry.width,t=geometry.top+d.top*geometry.height,r=geometry.left+d.right*geometry.width,b=geometry.top+d.bottom*geometry.height;
            int color=d.risk==Detection.Risk.NEAR?Color.rgb(255,99,110):d.risk==Detection.Risk.CAUTION?Color.rgb(255,200,87):Color.rgb(174,183,197);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((d.risk==Detection.Risk.NEAR?2.5f:1.5f)*dp);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);
            if(d.mask!=null)drawMask(canvas,d.mask,color,dp);
            else {
                float corner=Math.min(18*dp,Math.min(r-l,b-t)/3);Path p=new Path();
                p.moveTo(l,t+corner);p.lineTo(l,t);p.lineTo(l+corner,t);p.moveTo(r-corner,t);p.lineTo(r,t);p.lineTo(r,t+corner);
                p.moveTo(l,b-corner);p.lineTo(l,b);p.lineTo(l+corner,b);p.moveTo(r-corner,b);p.lineTo(r,b);p.lineTo(r,b-corner);canvas.drawPath(p,paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(11*dp);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            String label=d.label+(Float.isFinite(d.meters)?String.format(Locale.CHINA,"  %.1fm",d.meters):d.attention()?" · 未测距":"");
            float textWidth=Math.min(paint.measureText(label),geometry.width-12*dp);
            while(label.length()>1 && paint.measureText(label)>textWidth)label=label.substring(0,label.length()-1);
            float x=Math.max(geometry.left+2*dp,Math.min(l,geometry.left+geometry.width-textWidth-12*dp));
            float y=Math.max(geometry.top+19*dp,Math.min(t-5*dp,geometry.top+geometry.height-5*dp));
            paint.setColor(Color.argb(215,18,23,32));canvas.drawRoundRect(x,y-16*dp,x+textWidth+10*dp,y+5*dp,6*dp,6*dp,paint);
            paint.setColor(color);canvas.drawText(label,x+5*dp,y,paint);
        }
        canvas.restore();
    }
    private void drawMask(Canvas canvas,ObstacleMask mask,int color,float dp) {
        float sx=geometry.width/mask.width,sy=geometry.height/mask.height;Path edges=new Path(),fill=new Path();
        for(int y=0;y<mask.height;y++)for(int x=0;x<mask.width;x++)if(mask.at(x,y)) {
            float l=geometry.left+x*sx,t=geometry.top+y*sy,r=l+sx,b=t+sy;fill.addRect(l,t,r,b,Path.Direction.CW);
            if(!mask.at(x-1,y)){edges.moveTo(l,t);edges.lineTo(l,b);}if(!mask.at(x+1,y)){edges.moveTo(r,t);edges.lineTo(r,b);}
            if(!mask.at(x,y-1)){edges.moveTo(l,t);edges.lineTo(l+sx,t);}if(!mask.at(x,y+1)){edges.moveTo(l,b);edges.lineTo(r,b);}
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor((color&0xFFFFFF)|0x24000000);canvas.drawPath(fill,paint);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*dp);canvas.drawPath(edges,paint);
    }
}
