package com.cyanrbt.walkbuddy;

import android.graphics.Bitmap;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.TextureView;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/** One process-wide in-flight copy. Only the serial worker reads copied pixels. */
final class BrightnessReadback implements AutoCloseable {
    interface Listener {void completed(BrightnessReadback owner,boolean expired,int status,int total);}
    interface Copier {void request(Surface source,Bitmap target,IntConsumer finished,Handler callbackHandler);}
    private static final class Threads {
        static final Handler MAIN=new Handler(Looper.getMainLooper());
        static final HandlerThread THREAD=new HandlerThread("WalkBuddy brightness",android.os.Process.THREAD_PRIORITY_DEFAULT);
        static final Handler WORK;
        static {THREAD.start();WORK=new Handler(THREAD.getLooper());}
    }
    private static Job active;
    private final BrightnessFlight flight=new BrightnessFlight();
    private final LongSupplier clock;
    private final Copier copier;
    private Listener listener;
    private Bitmap bitmap;
    private int[] pixels;
    private Job pending;
    private Result last;
    private long lastDeliveryMs=-1;
    private boolean closed;

    BrightnessReadback(Listener listener){this(listener,SystemClock::elapsedRealtime,
        (source,target,done,handler)->PixelCopy.request(source,target,done::accept,handler));}
    BrightnessReadback(Listener listener,LongSupplier clock,Copier copier){
        this.listener=listener;this.clock=clock;this.copier=copier;
    }
    void observe(long frameAt){mainOnly();flight.observe(frameAt);}
    boolean ready(){mainOnly();return flight.due() && active==null;}
    boolean expired(long now){mainOnly();return flight.expired(now);}

    boolean request(TextureView source,long frameAt) {
        mainOnly();long now=clock.getAsLong();
        if(closed || active!=null || !flight.due())return false;
        if(!source.isAvailable() || source.getSurfaceTexture()==null)throw new IllegalStateException("Brightness source unavailable");
        BrightnessFlight.Ticket ticket=flight.begin(frameAt,now);if(ticket==null)return false;
        Surface surface=null;
        try {
            if(bitmap==null){bitmap=Bitmap.createBitmap(source.getResources().getDisplayMetrics(),16,12,Bitmap.Config.ARGB_8888);pixels=new int[192];}
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT);
            surface=new Surface(source.getSurfaceTexture());
            Job job=new Job(this,ticket,surface,bitmap,pixels,SystemClock.elapsedRealtimeNanos(),now-ticket.dueAt);
            pending=active=job;last=null;lastDeliveryMs=-1;
            if(!Threads.WORK.post(job::submit))throw new IllegalStateException("Brightness worker unavailable");
            return true;
        } catch(RuntimeException|Error error) {
            if(surface!=null)surface.release();
            if(active!=null && active.owner==this)active=null;
            pending=null;flight.complete(ticket,now);throw error;
        }
    }

    BrightnessTiming.Sample snapshot(long now) {
        mainOnly();Job job=pending;long wall=SystemClock.elapsedRealtimeNanos();
        if(job!=null){Result result=job.result;
            if(result!=null)return result.sample(BrightnessTiming.Stage.DELIVERING,now,millis(result.finishedNs,wall));
            long started=job.startedNs;
            return new BrightnessTiming.Sample(started<0?BrightnessTiming.Stage.QUEUED:BrightnessTiming.Stage.COPYING,
                age(job.ticket.frameAt,now),queueMillis(job,started<0?wall:started),started<0?-1:millis(started,wall),-1,-1,-1);
        }
        if(flight.due())return new BrightnessTiming.Sample(BrightnessTiming.Stage.WAITING,age(flight.dueAt(),now),
            age(flight.dueAt(),now),-1,-1,-1,-1);
        return last==null?BrightnessTiming.UNKNOWN:last.sample(BrightnessTiming.Stage.FINISHED,now,lastDeliveryMs);
    }
    private void delivered(Job job,Result result) {
        mainOnly();if(active!=job || pending!=job)return;
        long now=clock.getAsLong();BrightnessFlight.Outcome outcome=flight.complete(job.ticket,now);
        active=pending=null;last=result;lastDeliveryMs=millis(result.finishedNs,SystemClock.elapsedRealtimeNanos());
        if(closed){releaseBitmap();return;}
        Listener receiver=listener;
        if(receiver!=null && outcome!=BrightnessFlight.Outcome.UNRELATED && outcome!=BrightnessFlight.Outcome.RETIRED)
            receiver.completed(this,outcome==BrightnessFlight.Outcome.EXPIRED,result.status,result.total);
    }
    @Override public void close(){mainOnly();if(closed)return;closed=true;listener=null;flight.close();if(pending==null)releaseBitmap();}
    private void releaseBitmap(){if(bitmap!=null){bitmap.recycle();bitmap=null;}pixels=null;}
    private static void mainOnly(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Brightness owner requires main thread");}
    private static long millis(long from,long to){return from>=0 && to>=from?(to-from)/1_000_000:-1;}
    private static long queueMillis(Job job,long until){long delay=millis(job.queuedNs,until);return delay<0?-1:job.waitingMs+delay;}
    private static long age(long frame,long now){return now>=frame?now-frame:-1;}

    private static final class Result {
        final BrightnessFlight.Ticket ticket;final int status,total;
        final long queueMs,wallMs,cpuMs,finishedNs;
        Result(Job job,int status,int total,long ended,long cpuNs){
            this.ticket=job.ticket;this.status=status;this.total=total;finishedNs=ended;
            queueMs=queueMillis(job,job.startedNs);wallMs=millis(job.startedNs,ended);cpuMs=cpuNs<0?-1:cpuNs/1_000_000;
        }
        BrightnessTiming.Sample sample(BrightnessTiming.Stage stage,long now,long delivery){return new BrightnessTiming.Sample(
            stage,age(ticket.frameAt,now),queueMs,wallMs,cpuMs,delivery,status);}
    }
    private static final class Job {
        final BrightnessReadback owner;final BrightnessFlight.Ticket ticket;final Surface surface;final Bitmap bitmap;final int[] pixels;final long queuedNs,waitingMs;
        volatile long startedNs=-1;volatile Result result;
        private long callCpuNs;private boolean finished;
        Job(BrightnessReadback owner,BrightnessFlight.Ticket ticket,Surface surface,Bitmap bitmap,int[] pixels,long queuedNs,long waitingMs){
            this.owner=owner;this.ticket=ticket;this.surface=surface;this.bitmap=bitmap;this.pixels=pixels;this.queuedNs=queuedNs;this.waitingMs=waitingMs;
        }
        void submit(){startedNs=SystemClock.elapsedRealtimeNanos();long cpu=Debug.threadCpuTimeNanos();
            try{owner.copier.request(surface,bitmap,status->Threads.WORK.post(()->finish(status)),Threads.WORK);}
            catch(RuntimeException error){android.util.Log.w("WalkBuddyTiming","Brightness request error="+error.getClass().getSimpleName());
                Threads.WORK.post(()->finish(PixelCopy.ERROR_UNKNOWN));}
            finally{callCpuNs=Math.max(0,Debug.threadCpuTimeNanos()-cpu);}
        }
        void finish(int status){
            if(finished)return;finished=true;long cpu=Debug.threadCpuTimeNanos();int total=-1;
            try{if(status==PixelCopy.SUCCESS){bitmap.getPixels(pixels,0,16,0,0,16,12);total=0;
                for(int pixel:pixels)total+=((pixel>>16)&255)+((pixel>>8)&255)+(pixel&255);}}
            catch(RuntimeException error){status=PixelCopy.ERROR_UNKNOWN;total=-1;}
            finally{surface.release();}
            Result completed=new Result(this,status,total,SystemClock.elapsedRealtimeNanos(),callCpuNs+Math.max(0,Debug.threadCpuTimeNanos()-cpu));
            result=completed;Threads.MAIN.post(()->owner.delivered(this,completed));
        }
    }
}
