package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
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
    private final RectF rotatedBounds=new RectF();
    private final Matrix rotationTransform=new Matrix();
    private final Canvas rotationCanvas=new Canvas();
    private Bitmap rotationBitmap;
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
        if(rotation!=0)source=rotate(source,rotation);
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

    private Bitmap rotate(Bitmap source,int degrees) {
        int width=degrees%180==0?source.getWidth():source.getHeight();
        int height=degrees%180==0?source.getHeight():source.getWidth();
        ColorSpace colors=source.getColorSpace();if(colors==null)colors=ColorSpace.get(ColorSpace.Named.SRGB);
        if(rotationBitmap==null || rotationBitmap.getAllocationByteCount()<(long)width*height*4
            || !colors.equals(rotationBitmap.getColorSpace())) {
            rotationCanvas.setBitmap(null);if(rotationBitmap!=null)rotationBitmap.recycle();
            rotationBitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888,source.hasAlpha(),colors);
        } else if(rotationBitmap.getWidth()!=width || rotationBitmap.getHeight()!=height) {
            rotationCanvas.setBitmap(null);rotationBitmap.reconfigure(width,height,Bitmap.Config.ARGB_8888);
        }
        rotationBitmap.setDensity(source.getDensity());rotationBitmap.setHasAlpha(source.hasAlpha());
        rotationBitmap.setPremultiplied(source.isPremultiplied());rotationBitmap.eraseColor(Color.TRANSPARENT);
        rotationCanvas.setBitmap(rotationBitmap);rotationCanvas.setMatrix(null);
        destination.set(0,0,source.getWidth(),source.getHeight());rotationTransform.setRotate(-degrees);
        rotationTransform.mapRect(rotatedBounds,destination);
        // Preserve the former rasterize-then-letterbox order, including edge interpolation.
        rotationCanvas.translate(-rotatedBounds.left,-rotatedBounds.top);rotationCanvas.concat(rotationTransform);
        try {rotationCanvas.drawBitmap(source,null,destination,paint);}finally{rotationCanvas.setMatrix(null);}
        return rotationBitmap;
    }

    @Override public void close() {
        if(closed)return;
        closed=true;canvas.setBitmap(null);padded.recycle();rotationCanvas.setBitmap(null);
        if(rotationBitmap!=null){rotationBitmap.recycle();rotationBitmap=null;}
    }
}
