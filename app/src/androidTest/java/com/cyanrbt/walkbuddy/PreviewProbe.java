package com.cyanrbt.walkbuddy;
import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** 模拟器真实 TextureView/Surface 管线：合成生产者模拟系统已补偿 sensor 的纹理。 */
public class PreviewProbe extends Instrumentation {
 protected boolean preview, pip, overlay;
 @Override public void onCreate(Bundle args){super.onCreate(args);pip=args!=null && "true".equals(args.getString("pip"));overlay=args!=null && "true".equals(args.getString("overlay"));preview=args!=null && "true".equals(args.getString("preview"));start();}
 @Override public void onStart(){Bundle result=new Bundle();Activity activity=null;StringBuilder report=new StringBuilder();try{
  activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  final Activity host=activity;int cases=0;
  for(int sensor:new int[]{0,90,180,270})for(int display:new int[]{0,90,180,270})for(int[] size:new int[][]{{300,400},{280,210}}){
   int w=size[0],h=size[1],bw=sensor%180==0?640:480,bh=sensor%180==0?480:640;
   PreviewGeometry geometry=new PreviewGeometry(640,480,sensor,display,w,h);
   AtomicReference<TextureView> ref=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1),updated=new CountDownLatch(1);
   runOnMainSync(()->{FrameLayout frame=new FrameLayout(host);frame.setBackgroundColor(Color.BLACK);TextureView view=new TextureView(host);view.setOpaque(false);
    view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
     public void onSurfaceTextureAvailable(SurfaceTexture s,int x,int y){s.setDefaultBufferSize(bw,bh);Matrix m=new Matrix();m.setValues(geometry.matrix);view.setTransform(m);ready.countDown();}
     public void onSurfaceTextureSizeChanged(SurfaceTexture s,int x,int y){}
     public boolean onSurfaceTextureDestroyed(SurfaceTexture s){return true;}
     public void onSurfaceTextureUpdated(SurfaceTexture s){updated.countDown();}
    });frame.addView(view,new FrameLayout.LayoutParams(w,h));host.setContentView(frame);ref.set(view);});
   if(!ready.await(5,TimeUnit.SECONDS))throw new AssertionError("surface timeout");
   Surface surface=new Surface(ref.get().getSurfaceTexture());try{
    Canvas canvas=surface.lockCanvas(null);report.append("canvas=").append(canvas.getWidth()).append("x").append(canvas.getHeight()).append("\n");try{canvas.drawColor(Color.WHITE);Paint p=new Paint();p.setColor(Color.GREEN);canvas.drawCircle(bw/2f,bh/2f,60,p);
     p.setColor(Color.RED);canvas.drawRect(bw/2f-14,bh*.15f-14,bw/2f+14,bh*.15f+14,p);
     p.setColor(Color.BLACK);p.setTextSize(24);canvas.drawText("TOP / sensor="+sensor+" display="+display,8,32,p);
     canvas.drawLine(12,50,bw-12,50,p);
    }finally{surface.unlockCanvasAndPost(canvas);}
    if(!updated.await(5,TimeUnit.SECONDS))throw new AssertionError("frame timeout");
    CountDownLatch drawn=new CountDownLatch(1);runOnMainSync(()->ref.get().postOnAnimation(()->ref.get().postOnAnimation(drawn::countDown)));
    if(!drawn.await(5,TimeUnit.SECONDS))throw new AssertionError("draw timeout");
    AtomicReference<Bitmap> shot=new AtomicReference<>(),sample=new AtomicReference<>();CountDownLatch copied=new CountDownLatch(1);int[] copyResult={-1};
    runOnMainSync(()->{int[] location=new int[2];ref.get().getLocationInWindow(location);report.append("view=").append(ref.get().getWidth()).append("x").append(ref.get().getHeight()).append(" location=").append(java.util.Arrays.toString(location)).append(" matrix=").append(ref.get().getTransform(null)).append("\n");Bitmap capture=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);shot.set(capture);
     PixelCopy.request(host.getWindow(),new Rect(location[0],location[1],location[0]+w,location[1]+h),capture,status->{copyResult[0]=status;copied.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
     sample.set(PreviewActivity.samplePreview(ref.get(),geometry));});
    if(!copied.await(5,TimeUnit.SECONDS) || copyResult[0]!=PixelCopy.SUCCESS)throw new AssertionError("PixelCopy failed "+copyResult[0]);
    Bitmap image=shot.get(),input=sample.get();if(image==null || input==null)throw new AssertionError("bitmap unavailable");
    try(FileOutputStream stream=new FileOutputStream(new File(getTargetContext().getCacheDir(),"preview-last.png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}
    try(FileOutputStream stream=new FileOutputStream(new File(getTargetContext().getCacheDir(),"preview-sample-last.png"))){input.compress(Bitmap.CompressFormat.PNG,100,stream);}
    float[] marker=geometry.map(w/2f,h*.15f);int color=image.getPixel(Math.round(marker[0]),Math.round(marker[1]));
    if(Color.red(color)<180 || Color.green(color)>80)throw new AssertionError("方向标记错误 s="+sensor+" d="+display+" color="+color);
    int sx=Math.round((marker[0]-geometry.left)/geometry.width*input.getWidth()),sy=Math.round((marker[1]-geometry.top)/geometry.height*input.getHeight());
    int sampledColor=input.getPixel(sx,sy);if(Color.red(sampledColor)<180 || Color.green(sampledColor)>80)throw new AssertionError("识别采样方向不一致 s="+sensor+" d="+display+" color="+sampledColor);
    checkCircle(image);checkCircle(input);
    float expected=geometry.width/geometry.height;if(Math.abs(input.getWidth()/(float)input.getHeight()-expected)>0.015)throw new AssertionError("采样比例错误");
    File output=new File(getTargetContext().getCacheDir(),"preview-s"+sensor+"-d"+display+"-"+w+".png");
    try(FileOutputStream stream=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,stream);}
    report.append("PASS sensor=").append(sensor).append(" display=").append(display).append(" view=").append(w).append('x').append(h).append(" sample=").append(input.getWidth()).append('x').append(input.getHeight()).append('\n');
    image.recycle();input.recycle();cases++;
   }finally{surface.release();}
  }
  report.append("PREVIEW_PROBE_PASS cases=").append(cases).append("; direction marker + circle in displayed and sampled bitmap\n");result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("stream",report+"PREVIEW_PROBE_FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null){final Activity host=activity;runOnMainSync(host::finish);}}}
 private static void checkCircle(Bitmap image){int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
  for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getPixel(x,y);if(Color.green(c)>180 && Color.red(c)<80 && Color.blue(c)<80){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}
  if(right<left || Math.abs((right-left+1)/(float)(bottom-top+1)-1)>0.07)throw new AssertionError("圆形被拉伸："+(right-left+1)+"x"+(bottom-top+1));
 }
}
