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
 protected boolean preview, pip, pipRecovery, overlay, layout, recovery, home, threadParity, support, previewTrace;
 protected Integer profilePriority,profileThreads;
 protected boolean surfaceCopy;
 @Override public void onCreate(Bundle args){
  super.onCreate(args);support=enabled(args,"support");pip=enabled(args,"pip");pipRecovery=enabled(args,"pip_recovery");
  overlay=enabled(args,"overlay");layout=enabled(args,"layout");recovery=enabled(args,"recovery");home=enabled(args,"home");
  preview=enabled(args,"preview");threadParity=enabled(args,"thread_parity");previewTrace=enabled(args,"preview_trace");surfaceCopy=enabled(args,"surface_copy");
  if(previewTrace && !pipRecovery)throw new IllegalArgumentException("Preview tracing requires the recovery probe");
  if(surfaceCopy && !preview)throw new IllegalArgumentException("Surface copy comparison requires the preview probe");
  if(args!=null && args.containsKey("profile_priority")){profilePriority=Integer.valueOf(args.getString("profile_priority"));if(profilePriority!=1 && profilePriority!=10)throw new IllegalArgumentException("Only priorities 1/10 are profiled");}
  if(args!=null && args.containsKey("profile_threads")){profileThreads=Integer.valueOf(args.getString("profile_threads"));if(profileThreads!=1 && profileThreads!=2)throw new IllegalArgumentException("Only 1/2 CPU threads are profiled");}
  start();
 }
 private static boolean enabled(Bundle args,String name){return args!=null && "true".equals(args.getString(name));}
 @Override public void onStart(){Bundle result=new Bundle();Activity activity=null;StringBuilder report=new StringBuilder();try{
  activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  final Activity host=activity;int cases=0;long baselineNs=0,sampledNs=0;org.json.JSONArray brightnessSamples=new org.json.JSONArray(),surfaceCopies=new org.json.JSONArray();BrightnessSampler brightness=new BrightnessSampler();
  File evidence=new File(getTargetContext().getCacheDir(),"ui-probe");if(!evidence.isDirectory() && !evidence.mkdirs())throw new AssertionError("Evidence directory unavailable");
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
    AtomicReference<Bitmap> shot=new AtomicReference<>(),sample=new AtomicReference<>(),baseline=new AtomicReference<>();AtomicReference<BitmapFrame> raw=new AtomicReference<>();long[] copyNs=new long[2],limitedBytes={0};final int order=cases;CountDownLatch copied=new CountDownLatch(1);int[] copyResult={-1};
    runOnMainSync(()->{int[] location=new int[2];ref.get().getLocationInWindow(location);report.append("view=").append(ref.get().getWidth()).append("x").append(ref.get().getHeight()).append(" location=").append(java.util.Arrays.toString(location)).append(" matrix=").append(ref.get().getTransform(null)).append("\n");Bitmap capture=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);shot.set(capture);
     PixelCopy.request(host.getWindow(),new Rect(location[0],location[1],location[0]+w,location[1]+h),capture,status->{copyResult[0]=status;copied.countDown();},new android.os.Handler(android.os.Looper.getMainLooper()));
     // Alternate order to avoid always giving one path the first readback/cache state.
     if(order%2==0){copy(ref.get(),geometry,true,baseline,raw,copyNs,0);copy(ref.get(),geometry,false,baseline,raw,copyNs,1);}
     else {copy(ref.get(),geometry,false,baseline,raw,copyNs,1);copy(ref.get(),geometry,true,baseline,raw,copyNs,0);}
     sample.set(limitedBaseline(ref.get(),geometry,limitedBytes));});
    if(!copied.await(5,TimeUnit.SECONDS) || copyResult[0]!=PixelCopy.SUCCESS)throw new AssertionError("PixelCopy failed "+copyResult[0]);
    Bitmap image=shot.get(),input=sample.get(),original=baseline.get();if(image==null || input==null || original==null)throw new AssertionError("bitmap unavailable");
    BitmapFrame frame=raw.get();if(frame==null || frame.displayDegrees!=display)throw new AssertionError("Missing natural frame/rotation");
    if(frame.bitmap.getWidth()!=geometry.sampleWidth || frame.bitmap.getHeight()!=geometry.sampleHeight)throw new AssertionError("Readback changed natural dimensions");
    try(DetectorInput prepared=new DetectorInput()) {
     java.nio.ByteBuffer expected=DetectorProbe.baseline(input),actual=prepared.prepare(frame.bitmap,frame.displayDegrees);
     for(int i=0;i<expected.capacity();i++)if(expected.get(i)!=actual.get(i))throw new AssertionError("Texture input parity failed s="+sensor+" d="+display+" byte="+i);
     Bitmap storage=DetectorProbe.rotationBitmap(prepared);prepared.prepare(frame.bitmap,frame.displayDegrees);
     if(DetectorProbe.rotationBitmap(prepared)!=storage)throw new AssertionError("Repeated camera input reallocated rotation storage");
    }
    int naturalBytes=frame.bitmap.getAllocationByteCount();
    if(limitedBytes[0]!=naturalBytes*(display==0?1:2))throw new AssertionError("Unexpected legacy/new bitmap allocation: "+limitedBytes[0]+" vs "+naturalBytes);
    if(Math.max(input.getWidth(),input.getHeight())!=320 || input.getWidth()*input.getHeight()!=640*480/4)throw new AssertionError("Readback was not bounded to the model edge");
    if(input.getAllocationByteCount()*4!=original.getAllocationByteCount())throw new AssertionError("Expected quarter-sized readback bitmap");
    baselineNs+=copyNs[0];sampledNs+=copyNs[1];
    try(FileOutputStream stream=new FileOutputStream(new File(evidence,"preview-last.png"))){image.compress(Bitmap.CompressFormat.PNG,100,stream);}
    try(FileOutputStream stream=new FileOutputStream(new File(evidence,"preview-sample-last.png"))){input.compress(Bitmap.CompressFormat.PNG,100,stream);}
    float[] marker=geometry.map(w/2f,h*.15f);int color=image.getPixel(Math.round(marker[0]),Math.round(marker[1]));
    if(Color.red(color)<180 || Color.green(color)>80)throw new AssertionError("方向标记错误 s="+sensor+" d="+display+" color="+color);
    int sx=Math.round((marker[0]-geometry.left)/geometry.width*input.getWidth()),sy=Math.round((marker[1]-geometry.top)/geometry.height*input.getHeight());
    int sampledColor=input.getPixel(sx,sy);if(Color.red(sampledColor)<180 || Color.green(sampledColor)>80)throw new AssertionError("识别采样方向不一致 s="+sensor+" d="+display+" color="+sampledColor);
    checkCircle(image);checkCircle(input);checkCircle(original);
    float expected=geometry.width/geometry.height;if(Math.abs(input.getWidth()/(float)input.getHeight()-expected)>0.015)throw new AssertionError("采样比例错误");
    File output=new File(evidence,"preview-s"+sensor+"-d"+display+"-"+w+".png");
    try(FileOutputStream stream=new FileOutputStream(output)){image.compress(Bitmap.CompressFormat.PNG,100,stream);}
    report.append("PASS sensor=").append(sensor).append(" display=").append(display).append(" view=").append(w).append('x').append(h).append(" sample=").append(input.getWidth()).append('x').append(input.getHeight()).append(" baselineCopyUs=").append(copyNs[0]/1000).append(" naturalCopyUs=").append(copyNs[1]/1000)
     .append(" baselineBytes=").append(original.getAllocationByteCount()).append(" limitedBytes=").append(input.getAllocationByteCount())
     .append(" legacyLimitedAllocatedBytes=").append(limitedBytes[0]).append(" naturalAllocatedBytes=").append(naturalBytes).append("; worker-rotated input RGB identical\n");
    brightnessSamples.put(BrightnessProbe.compare(this,ref.get(),brightness,sensor,display,w,h));
    if(surfaceCopy)surfaceCopies.put(SurfaceCopyProbe.compare(this,ref.get(),sensor,display,w,h));
    image.recycle();input.recycle();original.recycle();frame.close();cases++;
   }finally{surface.release();}
  }
  BrightnessProbe.finish(brightness);
  try(FileOutputStream stream=new FileOutputStream(new File(evidence,"brightness-parity.json"))){stream.write(brightnessSamples.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  report.append(BrightnessProbe.synthetic(this,evidence));
  if(surfaceCopy){
   boolean accepted=true;for(int i=0;i<surfaceCopies.length();i++)accepted&=surfaceCopies.getJSONObject(i).getBoolean("accepted");
   try(FileOutputStream stream=new FileOutputStream(new File(evidence,"surface-copy-parity.json"))){stream.write(new org.json.JSONObject()
    .put("scope","Frozen synthetic producer; not camera, GPU improvement, or real-device acceptance")
    .put("accepted",accepted).put("cases",surfaceCopies).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   if(!accepted)throw new AssertionError("Experimental Surface copy changed frozen reference pixels; see surface-copy-parity.json");
   report.append("SURFACE_COPY_PARITY_PASS cases=32 pixels=6144; frozen producer, off-main request, exact ARGB and totals\n");
   org.json.JSONObject producers=SurfaceProducerProbe.run(this,host);
   try(FileOutputStream stream=new FileOutputStream(new File(evidence,"surface-copy-producers.json"))){stream.write(producers.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   if(!producers.getBoolean("accepted"))throw new AssertionError("Frozen Camera2/EGL Surface comparison failed; see surface-copy-producers.json");
   report.append("SURFACE_PRODUCERS_PARITY_PASS camera_cases=2 egl_cases=2 pixels=768; frozen independent producers, exact ARGB and stable timestamps\n");
  }
  report.append("BRIGHTNESS_TEXTURE_PARITY_PASS cases=32 pixels=6144; exact ARGB and total, one bitmap and array across changes, close recycled; frozen v0.4.36 reference\n");
  report.append("READBACK_OBSERVATION baselineUs=").append(baselineNs/1000).append(" naturalUs=").append(sampledNs/1000).append(" cases=32; alternating order, full baseline includes rotation, natural readback rotates later on worker; not camera end-to-end timing\n");
  report.append("ROTATION_BUFFER_PASS cases=32; all RGB input bytes identical to v0.4.16; one readback bitmap, worker rotation storage reused for display 90/180/270\n");
  report.append("PREVIEW_PROBE_PASS cases=").append(cases).append("; direction marker + circle in displayed and sampled bitmap\n");result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("stream",report+"PREVIEW_PROBE_FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}finally{if(activity!=null){final Activity host=activity;runOnMainSync(host::finish);}}}
 private static void copy(TextureView view,PreviewGeometry g,boolean old,AtomicReference<Bitmap> result,AtomicReference<BitmapFrame> raw,long[] times,int slot){
  long started=android.os.SystemClock.elapsedRealtimeNanos();
  if(old)result.set(baseline(view,g));else raw.set(PreviewActivity.samplePreview(view,g));
  times[slot]=android.os.SystemClock.elapsedRealtimeNanos()-started;
 }
 /** Frozen v0.4.16 bounded readback and rotation, only in the test APK. */
 private static Bitmap limitedBaseline(TextureView view,PreviewGeometry g,long[] allocated){
  Bitmap natural=view.getBitmap(g.sampleWidth,g.sampleHeight);if(natural==null)return null;
  allocated[0]=natural.getAllocationByteCount();if(g.displayDegrees==0)return natural;
  Matrix rotation=new Matrix();rotation.setRotate(-g.displayDegrees);
  try {Bitmap output=Bitmap.createBitmap(natural,0,0,natural.getWidth(),natural.getHeight(),rotation,true);if(output!=natural){allocated[0]+=output.getAllocationByteCount();natural.recycle();}return output;}
  catch(RuntimeException|Error error){natural.recycle();throw error;}
 }
 /** Frozen full-resolution v0.4.10 readback, only in the test APK. */
 private static Bitmap baseline(TextureView view,PreviewGeometry g){
  Bitmap natural=view.getBitmap(g.naturalWidth,g.naturalHeight);if(natural==null || g.displayDegrees==0)return natural;
  Matrix rotation=new Matrix();rotation.setRotate(-g.displayDegrees);
  try {Bitmap output=Bitmap.createBitmap(natural,0,0,natural.getWidth(),natural.getHeight(),rotation,true);if(output!=natural)natural.recycle();return output;}
  catch(RuntimeException|Error error){natural.recycle();throw error;}
 }
 private static void checkCircle(Bitmap image){int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
  int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
  for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=pixels[y*image.getWidth()+x];if(Color.green(c)>180 && Color.red(c)<80 && Color.blue(c)<80){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}
  if(right<left || Math.abs((right-left+1)/(float)(bottom-top+1)-1)>0.07)throw new AssertionError("圆形被拉伸："+(right-left+1)+"x"+(bottom-top+1));
 }
}
