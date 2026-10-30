package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.TextureView;

/** Main-thread workspace for the same 16x12 displayed-image check used before. */
class BrightnessSampler implements AutoCloseable {
    private Bitmap bitmap;
    private final int[] pixels=new int[16*12];
    private boolean closed;

    int read(TextureView source) {
        if(closed)throw new IllegalStateException("Brightness sampler closed");
        if(!source.isAvailable())return -1;
        if(bitmap==null)bitmap=Bitmap.createBitmap(source.getResources().getDisplayMetrics(),16,12,Bitmap.Config.ARGB_8888);
        // TextureView may leave the destination unchanged on failure; a fresh old-path bitmap was empty.
        bitmap.eraseColor(Color.TRANSPARENT);
        if(source.getBitmap(bitmap)==null)return -1;
        bitmap.getPixels(pixels,0,16,0,0,16,12);
        int total=0;
        for(int pixel:pixels)total+=((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255);
        return total;
    }
    @Override public void close() {
        if(closed)return;
        closed=true;
        if(bitmap!=null){bitmap.recycle();bitmap=null;}
    }
}
