package com.cyanrbt.walkbuddy;
import android.graphics.Bitmap;
final class BitmapFrame implements AutoCloseable {
    final Bitmap bitmap;
    BitmapFrame(Bitmap bitmap){this.bitmap=bitmap;}
    @Override public void close(){bitmap.recycle();}
}
