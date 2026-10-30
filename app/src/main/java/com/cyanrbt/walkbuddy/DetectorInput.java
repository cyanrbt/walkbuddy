package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** One worker owns this letterbox workspace for the lifetime of its detector. */
final class DetectorInput implements AutoCloseable {
    private static final int SIZE=PreviewGeometry.ANALYSIS_EDGE;
    private final Bitmap padded=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
    private final Canvas canvas=new Canvas(padded);
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private final int[] pixels=new int[SIZE*SIZE];
    private final byte[] rgb=new byte[SIZE*SIZE*3];
    private final ByteBuffer buffer=ByteBuffer.allocateDirect(SIZE*SIZE*3).order(ByteOrder.nativeOrder());
    private boolean closed;
    float width,height,padX,padY;

    ByteBuffer prepare(Bitmap source) {
        return prepare(source,0);
    }
    ByteBuffer prepare(Bitmap source,int displayDegrees) {
        if(closed)throw new IllegalStateException("识别输入已释放");
        if(displayDegrees%90!=0)throw new IllegalArgumentException("方向必须为90度整数倍");
        int rotation=Math.floorMod(displayDegrees,360);
        float scale=Math.min(SIZE/(float)source.getWidth(),SIZE/(float)source.getHeight());
        width=(rotation%180==0?source.getWidth():source.getHeight())*scale;
        height=(rotation%180==0?source.getHeight():source.getWidth())*scale;
        padX=(SIZE-width)/2;padY=(SIZE-height)/2;
        destination.set(padX,padY,padX+width,padY+height);
        // Clear the whole image, including padding left by a differently shaped previous frame.
        canvas.drawColor(Color.BLACK);
        if(rotation==0)canvas.drawBitmap(source,null,destination,paint);
        else {
            // Rotate directly into the reused model image; no intermediate camera bitmap.
            int saved=canvas.save();
            try {
                canvas.translate(padX,padY);canvas.scale(scale,scale);
                if(rotation==90)canvas.translate(0,source.getWidth());
                else if(rotation==180)canvas.translate(source.getWidth(),source.getHeight());
                else canvas.translate(source.getHeight(),0);
                canvas.rotate(-rotation);
                destination.set(0,0,source.getWidth(),source.getHeight());
                canvas.drawBitmap(source,null,destination,paint);
            } finally {canvas.restoreToCount(saved);}
        }
        padded.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);
        buffer.clear();
        int offset=0;
        for(int pixel:pixels){rgb[offset++]=(byte)(pixel>>16);rgb[offset++]=(byte)(pixel>>8);rgb[offset++]=(byte)pixel;}
        buffer.put(rgb); // One bulk copy, avoiding three direct-buffer calls for every pixel.
        buffer.rewind();return buffer;
    }

    @Override public void close() {
        if(closed)return;
        closed=true;canvas.setBitmap(null);padded.recycle();
    }
}
