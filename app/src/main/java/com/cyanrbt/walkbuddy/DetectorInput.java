package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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
    private final RectF orientedSource=new RectF();
    private final Matrix inputTransform=new Matrix(),rotationTransform=new Matrix();
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
                int orientedWidth=rotation%180==0?source.getWidth():source.getHeight();
                int orientedHeight=rotation%180==0?source.getHeight():source.getWidth();
                orientedSource.set(0,0,orientedWidth,orientedHeight);
                // Use the same rounded destination rectangle as the former second draw.
                // Scaling by the unrounded factor changes edge interpolation on odd sizes.
                inputTransform.setRectToRect(orientedSource,destination,Matrix.ScaleToFit.FILL);
                rotationTransform.setRotate(-rotation);
                if(rotation==90)rotationTransform.postTranslate(0,source.getWidth());
                else if(rotation==180)rotationTransform.postTranslate(source.getWidth(),source.getHeight());
                else rotationTransform.postTranslate(source.getHeight(),0);
                inputTransform.preConcat(rotationTransform);canvas.concat(inputTransform);
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
