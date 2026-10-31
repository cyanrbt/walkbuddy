package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.TextureView;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.IntConsumer;

/** Controlled adapter cases plus one actual frozen Surface copy. Not a GPU benchmark. */
final class BrightnessReadbackProbe {
    static String run(Instrumentation runner,TextureView view) throws Exception {
        AtomicLong clock=new AtomicLong(1000);AtomicInteger callbacks=new AtomicInteger();AtomicReference<Delivery> delivered=new AtomicReference<>();
        FakeCopy fake=new FakeCopy();
        BrightnessReadback reader=SurfaceCopyProbe.onMain(runner,()->new BrightnessReadback((owner,expired,status,total)->{
            if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("Brightness result was not delivered on main");
            delivered.set(new Delivery(expired,status,total));callbacks.incrementAndGet();
        },clock::get,fake));
        Bitmap storage=null;Object pixels=null;int cases=0;
        try {
            for(int color:new int[]{Color.BLACK,Color.WHITE,0xff010101,0xff020202,0xff030303,0xff040404,
                    Color.RED,Color.GREEN,Color.BLUE,Color.TRANSPARENT,0x7f2f8e61}) {
                Arrays.fill(fake.content,color);check(runner,view,reader,fake,clock,callbacks,delivered,PixelCopy.SUCCESS);cases++;
                if(storage==null){storage=SurfaceCopyProbe.onMain(runner,()->(Bitmap)field(reader,"bitmap"));pixels=field(reader,"pixels");}
                final Bitmap expected=storage;final Object expectedPixels=pixels;
                SurfaceCopyProbe.onMain(runner,()->{if(field(reader,"bitmap")!=expected || field(reader,"pixels")!=expectedPixels)throw new AssertionError("Brightness workspace reallocated");return null;});
            }
            Arrays.fill(fake.content,0xff030303);fake.content[0]=0xff020303;
            if(check(runner,view,reader,fake,clock,callbacks,delivered,0)!=1727)throw new AssertionError("Below-dark boundary changed");cases++;
            fake.content[0]=0xff040303;
            if(check(runner,view,reader,fake,clock,callbacks,delivered,0)!=1729)throw new AssertionError("Above-dark boundary changed");cases++;
            for(int i=0;i<192;i++)fake.content[i]=i*93847;
            check(runner,view,reader,fake,clock,callbacks,delivered,0);cases++;
            fake.rows=6;check(runner,view,reader,fake,clock,callbacks,delivered,0);fake.rows=12;cases++;
            fake.write=false;if(check(runner,view,reader,fake,clock,callbacks,delivered,0)!=0)throw new AssertionError("Unwritten pixels retained old brightness");fake.write=true;cases++;
            fake.status=PixelCopy.ERROR_SOURCE_NO_DATA;check(runner,view,reader,fake,clock,callbacks,delivered,fake.status);fake.status=0;cases++;
            fake.throwCopy=true;check(runner,view,reader,fake,clock,callbacks,delivered,PixelCopy.ERROR_UNKNOWN);fake.throwCopy=false;cases++;
            Arrays.fill(fake.content,Color.WHITE);check(runner,view,reader,fake,clock,callbacks,delivered,0);cases++;
            fake.expire=true;check(runner,view,reader,fake,clock,callbacks,delivered,0);fake.expire=false;
            if(!delivered.get().expired)throw new AssertionError("Late copy was accepted as current");
        } finally {SurfaceCopyProbe.onMain(runner,()->{reader.close();return null;});}
        if(storage==null || !storage.isRecycled())throw new AssertionError("Closed reader retained copied bitmap");
        verifyRetirement(runner,view);
        verifyActualCopy(runner,view);
        return "BRIGHTNESS_ASYNC_COMPONENT_PASS cases="+cases+" exact_rgb=true workspace_reused=true empty_partial_cleared=true expiry=true retired_slot_released=true actual_surface=true\n";
    }
    private static int check(Instrumentation runner,TextureView view,BrightnessReadback reader,FakeCopy fake,AtomicLong clock,
            AtomicInteger callbacks,AtomicReference<Delivery> delivered,int expectedStatus) throws Exception {
        int before=callbacks.get();clock.addAndGet(1000);fake.clock=clock;
        Bitmap reference=Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888);
        int expected;
        try{if(fake.write)reference.setPixels(fake.content,0,16,0,0,16,fake.rows);expected=BrightnessProbe.legacyTotal(reference);}
        finally{reference.recycle();}
        SurfaceCopyProbe.onMain(runner,()->{reader.observe(clock.get());if(!reader.ready() || !reader.request(view,clock.get()))throw new AssertionError("Brightness request rejected");return null;});
        await(()->callbacks.get()>before,"controlled brightness completion");
        Delivery result=delivered.get();
        if(result.status!=expectedStatus || !fake.offMain.get())throw new AssertionError("Brightness status/thread changed");
        if(expectedStatus==0 && result.total!=expected || expectedStatus!=0 && result.total!=-1)throw new AssertionError("Brightness copied RGB sum changed");
        return result.total;
    }
    private static void verifyRetirement(Instrumentation runner,TextureView view) throws Exception {
        AtomicLong clock=new AtomicLong(50000);AtomicReference<IntConsumer> held=new AtomicReference<>();
        AtomicInteger oldCalls=new AtomicInteger(),newCalls=new AtomicInteger();AtomicBoolean heartbeat=new AtomicBoolean();
        BrightnessReadback old=SurfaceCopyProbe.onMain(runner,()->new BrightnessReadback((owner,expired,status,total)->oldCalls.incrementAndGet(),clock::get,
            (surface,target,done,handler)->held.set(done)));
        BrightnessReadback next=SurfaceCopyProbe.onMain(runner,()->new BrightnessReadback((owner,expired,status,total)->newCalls.incrementAndGet(),clock::get,
            (surface,target,done,handler)->{target.eraseColor(Color.WHITE);done.accept(0);}));
        Bitmap retired=null;
        try {
            SurfaceCopyProbe.onMain(runner,()->{old.observe(clock.get());if(!old.request(view,clock.get()))throw new AssertionError("Held copy did not start");return null;});
            await(()->held.get()!=null,"held adapter request");
            retired=SurfaceCopyProbe.onMain(runner,()->(Bitmap)field(old,"bitmap"));final Bitmap pending=retired;
            SurfaceCopyProbe.onMain(runner,()->{heartbeat.set(true);old.close();next.observe(clock.get());
                if(pending.isRecycled() || field(old,"listener")!=null || next.ready() || next.request(view,clock.get()) || field(next,"bitmap")!=null)
                    throw new AssertionError("Retired in-flight copy lost exclusive storage or retained its receiver");return null;});
            if(!heartbeat.get())throw new AssertionError("Main heartbeat blocked behind brightness adapter");
            held.getAndSet(null).accept(0);
            await(()->pending.isRecycled(),"retired buffer released after callback");
            SurfaceCopyProbe.onMain(runner,()->{if(oldCalls.get()!=0 || !next.ready() || !next.request(view,clock.get()))throw new AssertionError("Retired copy interfered with new owner");return null;});
            await(()->newCalls.get()==1,"new owner completion");
        } finally {
            IntConsumer callback=held.getAndSet(null);if(callback!=null)callback.accept(0);
            SurfaceCopyProbe.onMain(runner,()->{old.close();next.close();return null;});
        }
        if(retired==null || !retired.isRecycled())throw new AssertionError("Retired copy did not finish cleanup");
    }
    static void verifyActualCopy(Instrumentation runner,TextureView view) throws Exception {
        int[] reference=SurfaceCopyProbe.onMain(runner,()->SurfaceCopyProbe.reference(view));AtomicReference<int[]> copied=new AtomicReference<>();
        AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch completed=new CountDownLatch(1);AtomicBoolean offMain=new AtomicBoolean();
        BrightnessReadback reader=SurfaceCopyProbe.onMain(runner,()->new BrightnessReadback((owner,expired,status,total)->{
            try{if(expired || status!=0)throw new AssertionError("Actual brightness copy failed or expired");
                Bitmap bitmap=(Bitmap)field(owner,"bitmap");int[] pixels=new int[192];bitmap.getPixels(pixels,0,16,0,0,16,12);copied.set(pixels);
                if(total!=SurfaceCopyProbe.total(pixels))throw new AssertionError("Actual copied brightness sum changed");
                BrightnessTiming.Sample timing=owner.snapshot(SystemClock.elapsedRealtime());
                if(timing.stage!=BrightnessTiming.Stage.FINISHED || timing.queueMs<0 || timing.wallMs<0 || timing.cpuMs<0 || timing.deliveryMs<0)
                    throw new AssertionError("Actual asynchronous timing missing");
            }catch(Throwable error){failure.set(error);}finally{completed.countDown();}
        },SystemClock::elapsedRealtime,(surface,target,done,handler)->{offMain.set(Looper.myLooper()!=Looper.getMainLooper());PixelCopy.request(surface,target,done::accept,handler);}));
        try{SurfaceCopyProbe.onMain(runner,()->{long at=SystemClock.elapsedRealtime();reader.observe(at);if(!reader.request(view,at))throw new AssertionError("Actual request rejected");return null;});
            if(!completed.await(5,TimeUnit.SECONDS))throw new AssertionError("Actual brightness copy timeout");
            if(failure.get()!=null)throw new AssertionError(failure.get());
            int[] after=SurfaceCopyProbe.onMain(runner,()->SurfaceCopyProbe.reference(view));
            if(!offMain.get() || !Arrays.equals(reference,after) || !Arrays.equals(reference,copied.get()))throw new AssertionError("Actual reader changed frozen Surface pixels");
        }finally{SurfaceCopyProbe.onMain(runner,()->{reader.close();return null;});}
    }
    private static final class Delivery {final boolean expired;final int status,total;Delivery(boolean expired,int status,int total){this.expired=expired;this.status=status;this.total=total;}}
    private static final class FakeCopy implements BrightnessReadback.Copier {
        final int[] content=new int[192];final AtomicBoolean offMain=new AtomicBoolean();AtomicLong clock;
        int rows=12,status;boolean write=true,throwCopy,expire;
        @Override public void request(android.view.Surface source,Bitmap target,IntConsumer done,android.os.Handler handler){
            offMain.set(Looper.myLooper()!=Looper.getMainLooper());if(throwCopy)throw new IllegalStateException("Injected async copy failure");
            if(write)target.setPixels(content,0,16,0,0,16,rows);if(expire)clock.addAndGet(1000);done.accept(status);
        }
    }
    private static Object field(Object source,String name){try{Field f=source.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(source);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static void await(java.util.function.BooleanSupplier ready,String label) throws Exception {long until=SystemClock.elapsedRealtime()+5000;
        while(!ready.getAsBoolean() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(10);
        if(!ready.getAsBoolean())throw new AssertionError("Timed out: "+label);}
}
