package com.cyanrbt.walkbuddy;
import android.graphics.Bitmap;
final class BitmapFrame implements AutoCloseable {
    final Bitmap bitmap;
    final int displayDegrees;
    BitmapFrame(Bitmap bitmap){this(bitmap,0);}
    BitmapFrame(Bitmap bitmap,int displayDegrees){
        if(displayDegrees%90!=0)throw new IllegalArgumentException("方向必须为90度整数倍");
        this.bitmap=bitmap;this.displayDegrees=Math.floorMod(displayDegrees,360);
    }
    @Override public void close(){bitmap.recycle();}
}
