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
        if(closed)throw new IllegalStateException("识别输入已释放");
        float scale=Math.min(SIZE/(float)source.getWidth(),SIZE/(float)source.getHeight());
        width=source.getWidth()*scale;height=source.getHeight()*scale;
        padX=(SIZE-width)/2;padY=(SIZE-height)/2;
        destination.set(padX,padY,padX+width,padY+height);
        // Clear the whole image, including padding left by a differently shaped previous frame.
        canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(source,null,destination,paint);
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
