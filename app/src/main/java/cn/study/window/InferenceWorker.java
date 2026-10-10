package cn.study.window;

/** 一个进程只创建一个实例；单worker，最多一个待处理帧。纯Java便于阻塞fake回归。 */
public final class InferenceWorker<F extends AutoCloseable, R> {
    public interface Clock { long now(); }
    public interface Detector<F,R> extends AutoCloseable { R detect(F frame) throws Exception; }
    public interface Factory<F,R> { Detector<F,R> create() throws Exception; }
    public interface Callback<R> { void complete(R result, Throwable error); }
    public static final class Ticket {
        public volatile boolean cancelled;
        public volatile long captureAt, displayAt, analysisAt;
        public Ticket(long now) { captureAt=displayAt=analysisAt=now; }
        public boolean allowed(long now) { return !cancelled && now-captureAt<=1200 && now-displayAt<=1200 && now-analysisAt<=1500; }
    }
    private final Object lock=new Object();
    private final Clock clock;
    private final Factory<F,R> factory;
    private final Thread thread;
    private Job pending, active;
    private boolean shutdown, releaseIdle, maintenance;
    private Detector<F,R> detector;
    private Ticket owner;
    private final class Job {
        final Ticket ticket; final F frame; final long frameAt;
        Callback<R> callback;
        Job(Ticket t,F f,long at,Callback<R> c) {ticket=t;frame=f;frameAt=at;callback=c;}
        boolean allowed() {long now=clock.now();return ticket.allowed(now) && now-frameAt<=1000;}
    }
    public InferenceWorker(Clock clock,Factory<F,R> factory) {
        this.clock=clock;this.factory=factory;
        thread=new Thread(this::loop,"本地识别唯一worker");thread.setDaemon(true);thread.start();
    }
    /** 总是取得frame所有权：拒绝/取消/完成都会恰好释放一次。 */
    public boolean submit(Ticket ticket,F frame,long frameAt,Callback<R> callback) {
        synchronized(lock) {
            if(shutdown || pending!=null || !ticket.allowed(clock.now()) || clock.now()-frameAt>1000) {close(frame);return false;}
            pending=new Job(ticket,frame,frameAt,callback);lock.notifyAll();return true;
        }
    }
    public boolean busy() {synchronized(lock){return active!=null || pending!=null || releaseIdle || maintenance;}}
    public int pendingCount() {synchronized(lock){return pending==null?0:1;}}
    public void cancel(Ticket ticket) {
        ticket.cancelled=true;
        synchronized(lock) {
            if(pending!=null && pending.ticket==ticket) {Job job=pending;pending=null;job.callback=null;close(job.frame);}
            if(active!=null && active.ticket==ticket) active.callback=null;
            releaseIdle=true;lock.notifyAll();
        }
    }
    /** 仅测试/进程退出使用；service停止只cancel，不创建新worker，也不承诺中断native。 */
    public void shutdown() {synchronized(lock){shutdown=true;if(pending!=null){close(pending.frame);pending=null;}if(active!=null){active.ticket.cancelled=true;active.callback=null;}lock.notifyAll();}}
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long end=System.nanoTime()+timeoutMillis*1000000L;
        synchronized(lock){while(active!=null || pending!=null || releaseIdle || maintenance){long left=(end-System.nanoTime())/1000000L;if(left<=0)return false;lock.wait(left);}return true;}
    }
    private void loop() {
        while(true) {
            Job job; boolean quit,clean;
            synchronized(lock) {
                while(pending==null && !releaseIdle && !shutdown) {try{lock.wait();}catch(InterruptedException ignored){}}
                quit=shutdown;clean=releaseIdle && pending==null;
                releaseIdle=false;
                maintenance=quit||clean;
                job=quit||clean?null:pending;
                if(job!=null){pending=null;active=job;}
            }
            // native创建/调用/关闭都不持有lock，停止入口不会等待native返回。
            if(quit||clean){closeDetector();synchronized(lock){maintenance=false;lock.notifyAll();}if(quit)return;continue;}
            R result=null;Throwable failure=null;
            try {
                if(job.allowed()) {
                    if(owner!=job.ticket){closeDetector();if(job.allowed()){detector=factory.create();owner=job.ticket;}}
                    if(detector!=null && job.allowed())result=detector.detect(job.frame);
                }
            } catch(Exception|LinkageError error){failure=error;}
            finally{close(job.frame);}
            boolean release;
            synchronized(lock) {
                if(job.callback!=null && job.allowed() && (result!=null || failure!=null))job.callback.complete(result,failure);
                job.callback=null;release=job.ticket.cancelled || failure!=null || !job.allowed();
            }
            if(release)closeDetector();
            synchronized(lock){active=null;lock.notifyAll();}
        }
    }
    private void closeDetector(){if(detector!=null){close(detector);detector=null;}owner=null;}
    private static void close(AutoCloseable value){try{value.close();}catch(Exception ignored){}}
}
