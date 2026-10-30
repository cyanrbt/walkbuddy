package com.cyanrbt.walkbuddy;

/** One process-wide audio owner; no native audio operation runs under the control lock. */
final class TonePlayer {
    interface Clock {long now();}
    interface Output extends AutoCloseable {boolean play();void stop();void close();}
    interface Factory {Output create() throws Exception;}
    interface Failure {void failed(Session session,Throwable error);}
    static final class Session {
        final InferenceWorker.Ticket frames;
        volatile boolean cancelled,failed;
        volatile Failure failure;
        private Detection.Risk observed=Detection.Risk.CAPTURED;
        Session(InferenceWorker.Ticket frames,Failure failure){this.frames=frames;this.failure=failure;}
        boolean allowed(long now){return !cancelled && !failed && frames!=null && frames.allowed(now);}
    }
    private static final class Job {
        final Session session;final boolean play;final long createdAt,until;
        volatile boolean cancelled;
        Job(Session session,boolean play,long now,long until){this.session=session;this.play=play;createdAt=now;this.until=until;}
        boolean allowed(long now){return !cancelled && session.allowed(now) && now>=createdAt && (!play || now<=until);}
    }
    private final Object lock=new Object();
    private final Clock clock;private final Factory factory;
    private Job pending,active;
    private Session stopping;
    private boolean cleanup,shutdown,maintenance,terminated;
    private volatile Session owner;
    private Output output; // Audio thread only.
    TonePlayer(Clock clock,Factory factory) {
        this.clock=clock;this.factory=factory;
        Thread thread=new Thread(this::loop,"WalkBuddy sound");thread.setDaemon(true);thread.start();
    }
    boolean prepare(Session session) {
        synchronized(lock){
            if(shutdown || session==null || !session.allowed(clock.now()) || pending!=null)return false;
            pending=new Job(session,false,clock.now(),Long.MAX_VALUE);lock.notifyAll();return true;
        }
    }
    boolean play(Session session,long until) {
        synchronized(lock){
            long now=clock.now();
            if(shutdown || session==null || !session.allowed(now) || until<now)return false;
            if(pending!=null)pending.cancelled=true;
            if(active!=null && active.play)active.cancelled=true;
            pending=new Job(session,true,now,until);lock.notifyAll();return true;
        }
    }
    void observe(Session session,Detection.Risk risk) {
        if(session==null)return;
        synchronized(lock){
            if(risk.ordinal()<session.observed.ordinal()) {
                discardLocked(session);stopping=session;lock.notifyAll();
            }
            session.observed=risk;
        }
    }
    void cancel(Session session) {
        if(session==null)return;
        session.cancelled=true;session.failure=null;
        synchronized(lock){discardLocked(session);cleanup=true;lock.notifyAll();}
    }
    private void discardLocked(Session session) {
        if(pending!=null && pending.session==session){pending.cancelled=true;pending=null;}
        if(active!=null && active.session==session)active.cancelled=true;
    }
    int pendingCount(){synchronized(lock){return pending==null?0:1;}}
    void shutdown() {
        synchronized(lock){
            shutdown=true;
            if(pending!=null){pending.session.cancelled=true;pending.session.failure=null;pending=null;}
            if(active!=null){active.session.cancelled=true;active.session.failure=null;active.cancelled=true;}
            if(owner!=null){owner.cancelled=true;owner.failure=null;}
            lock.notifyAll();
        }
    }
    boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long end=System.nanoTime()+timeoutMillis*1000000L;
        synchronized(lock){
            while(active!=null || pending!=null || cleanup || stopping!=null || maintenance || (shutdown && !terminated)) {
                long left=(end-System.nanoTime())/1000000L;if(left<=0)return false;lock.wait(left);
            }
            return true;
        }
    }
    private void loop() {
        while(true) {
            Job job;Session stop;boolean quit;
            synchronized(lock){
                while(pending==null && !cleanup && stopping==null && !shutdown)try{lock.wait();}catch(InterruptedException ignored){}
                quit=shutdown;stop=stopping;stopping=null;cleanup=false;
                job=quit?null:pending;pending=null;active=job;maintenance=true;
            }
            if(quit){closeOutput();synchronized(lock){maintenance=false;active=null;terminated=true;lock.notifyAll();}return;}
            Session failing=job!=null?job.session:owner;
            try {
                if(owner!=null && !owner.allowed(clock.now()))closeOutput();
                if(stop!=null && owner==stop && output!=null){failing=stop;output.stop();}
                if(job!=null && job.session.allowed(clock.now())) {
                    failing=job.session;
                    if(owner!=job.session){
                        closeOutput();
                        if(job.session.allowed(clock.now())){output=factory.create();owner=job.session;if(output==null)throw new IllegalStateException("Missing tone output");}
                    }
                    if(output!=null && job.play && job.allowed(clock.now()) && !output.play())
                        throw new IllegalStateException("Tone playback rejected");
                }
            } catch(Exception|LinkageError error){closeOutput();fail(failing,error);}
            finally {
                if(owner!=null && !owner.allowed(clock.now()))closeOutput();
                synchronized(lock){active=null;maintenance=false;lock.notifyAll();}
            }
        }
    }
    private void fail(Session session,Throwable error) {
        if(session==null)return;Failure callback;
        synchronized(lock){
            if(session.cancelled || session.failed)return;
            session.failed=true;callback=session.failure;session.failure=null;discardLocked(session);
        }
        if(callback!=null && !session.cancelled)try{callback.failed(session,error);}catch(RuntimeException ignored){}
    }
    private void closeOutput() {
        Output retired=output;output=null;owner=null;
        if(retired!=null)try{retired.close();}catch(Exception|LinkageError ignored){}
    }
}
