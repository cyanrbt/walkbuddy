package cn.study.window;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Collections;
import java.util.List;

/** Uses the same upright image rectangle as PreviewGeometry, including PiP letterboxing. */
public final class DetectionOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Detection> detections=Collections.emptyList();
    private PreviewGeometry geometry;
    private boolean enabled;
    public DetectionOverlay(Context context,AttributeSet attrs){super(context,attrs);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void show(List<Detection> detections,PreviewGeometry geometry){this.detections=detections;this.geometry=geometry;enabled=true;invalidate();}
    void clear(){detections=Collections.emptyList();enabled=false;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!enabled || geometry==null)return;
        float density=getResources().getDisplayMetrics().density;
        canvas.save();canvas.clipRect(geometry.left,geometry.top,geometry.left+geometry.width,geometry.top+geometry.height);
        for(Detection d:detections) {
            float l=geometry.left+d.left*geometry.width,t=geometry.top+d.top*geometry.height;
            float r=geometry.left+d.right*geometry.width,b=geometry.top+d.bottom*geometry.height;
            int color=d.attention()?Color.rgb(255,199,77):Color.rgb(110,230,202);
            paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);canvas.drawRect(l,t,r,b,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(12*density);
            String label=d.label+(d.attention()?" · 关注":" · 检测");
            float textWidth=paint.measureText(label),x=Math.max(geometry.left,Math.min(l,geometry.left+geometry.width-textWidth-8*density));
            float y=Math.min(geometry.top+geometry.height,Math.max(t,geometry.top)+18*density);
            paint.setColor(Color.argb(210,10,20,25));canvas.drawRect(x,y-17*density,x+textWidth+8*density,y+3*density,paint);
            paint.setColor(color);canvas.drawText(label,x+4*density,y,paint);
        }
        canvas.restore();
    }
}
