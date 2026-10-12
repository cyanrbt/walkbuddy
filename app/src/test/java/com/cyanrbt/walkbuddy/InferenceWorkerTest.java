package com.cyanrbt.walkbuddy;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class InferenceWorkerTest {
    static final class Frame implements AutoCloseable {final AtomicInteger released=new AtomicInteger();public void close(){assertEquals(1,released.incrementAndGet());}}
    static void await(CountDownLatch latch)throws InterruptedException{assertTrue(latch.await(3,TimeUnit.SECONDS));}
    @Test public void cancelledQueueDoesNotInferAndActiveFrameIsNotRecycledDuringNative()throws Exception{
        AtomicLong now=new AtomicLong(100);AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),results=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker<Frame,String> w=new InferenceWorker<>(now::get,()->{created.incrementAndGet();return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f)throws Exception{entered.countDown();await(release);assertEquals(0,f.released.get());return "人";}public void close(){closed.incrementAndGet();}};});
        try{InferenceWorker.Ticket a=new InferenceWorker.Ticket(100),b=new InferenceWorker.Ticket(100);Frame fa=new Frame(),fb=new Frame();assertTrue(w.submit(a,fa,100,(r,e)->results.incrementAndGet()));await(entered);assertTrue(w.submit(b,fb,100,(r,e)->results.incrementAndGet()));w.cancel(b);w.cancel(a);assertEquals(1,fb.released.get());assertEquals(0,fa.released.get());assertEquals(0,w.pendingCount());assertEquals(0,closed.get());release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(1,fa.released.get());assertEquals(1,closed.get());assertEquals(0,results.get());assertEquals(1,created.get());}finally{release.countDown();w.shutdown();}
    }
    @Test public void cancellationDuringFactoryPreventsNativeCall()throws Exception{
        AtomicInteger called=new AtomicInteger(),closed=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);InferenceWorker.Ticket ticket=new InferenceWorker.Ticket(100);Frame f=new Frame();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f){called.incrementAndGet();return "人";}public void close(){closed.incrementAndGet();}};});
        try{w.submit(ticket,f,100,(r,e)->fail("取消后不应提交"));await(entered);w.cancel(ticket);release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(0,called.get());assertEquals(1,closed.get());assertEquals(1,f.released.get());}finally{release.countDown();w.shutdown();}
    }
    @Test public void deadlineDuringFactoryPreventsNativeCall()throws Exception{
        AtomicLong now=new AtomicLong(100);AtomicInteger called=new AtomicInteger(),closed=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Frame f=new Frame();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(now::get,()->{entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f){called.incrementAndGet();return "人";}public void close(){closed.incrementAndGet();}};});
        try{w.submit(new InferenceWorker.Ticket(100),f,100,(r,e)->fail("逾期后不应提交"));await(entered);now.set(1201);release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(0,called.get());assertEquals(1,closed.get());assertEquals(1,f.released.get());}finally{release.countDown();w.shutdown();}
    }
    @Test public void oneHundredRestartRequestsDoNotCreateParallelNativeWorkers()throws Exception{
        AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),results=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean first=new AtomicBoolean(true);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f)throws Exception{if(first.getAndSet(false)){entered.countDown();await(release);}return "人";}public void close(){closed.incrementAndGet();}};});
        try{InferenceWorker.Ticket original=new InferenceWorker.Ticket(100);w.submit(original,new Frame(),100,(r,e)->fail("已停止结果"));await(entered);w.cancel(original);for(int i=0;i<100;i++){InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);Frame f=new Frame();assertTrue(w.submit(t,f,100,(r,e)->fail("已取消结果")));assertEquals(1,w.pendingCount());w.cancel(t);assertEquals(1,f.released.get());assertEquals(0,w.pendingCount());}assertEquals(1,created.get());release.countDown();assertTrue(w.awaitIdle(3000));assertTrue(w.submit(new InferenceWorker.Ticket(100),new Frame(),100,(r,e)->results.incrementAndGet()));assertTrue(w.awaitIdle(3000));assertEquals(2,created.get());assertEquals(1,results.get());assertEquals(1,closed.get());}finally{release.countDown();w.shutdown();}
    }
    @Test public void staleQueuedFrameIsReleasedWithoutNewModel()throws Exception{
        AtomicLong now=new AtomicLong(100);AtomicInteger created=new AtomicInteger(),calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Frame queued=new Frame();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(now::get,()->{created.incrementAndGet();return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f)throws Exception{calls.incrementAndGet();entered.countDown();await(release);return "人";}public void close(){}};});
        try{w.submit(new InferenceWorker.Ticket(100),new Frame(),100,(r,e)->fail("过期结果"));await(entered);w.submit(new InferenceWorker.Ticket(100),queued,100,(r,e)->fail("过期排队帧"));now.set(1201);release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(1,created.get());assertEquals(1,calls.get());assertEquals(1,queued.released.get());}finally{release.countDown();w.shutdown();}
    }
    @Test(timeout=5000) public void nativeCloseDoesNotHoldCancellationLock()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f){return "人";}public void close()throws Exception{entered.countDown();await(release);}});
        try{w.submit(t,new Frame(),100,(r,e)->{});assertTrue(w.awaitIdle(3000));w.cancel(t);await(entered);w.cancel(new InferenceWorker.Ticket(100));release.countDown();assertTrue(w.awaitIdle(3000));}finally{release.countDown();w.shutdown();}
    }
    @Test public void factoryFailureReleasesFrameAndAllowsRecovery()throws Exception{
        AtomicInteger attempts=new AtomicInteger(),failures=new AtomicInteger(),results=new AtomicInteger();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{if(attempts.incrementAndGet()==1)throw new IllegalStateException("fake加载失败");return new InferenceWorker.Detector<Frame,String>(){public String detect(Frame f){return "人";}public void close(){}};});
        try{Frame a=new Frame();w.submit(new InferenceWorker.Ticket(100),a,100,(r,e)->{if(e!=null)failures.incrementAndGet();});assertTrue(w.awaitIdle(3000));assertEquals(1,failures.get());assertEquals(1,a.released.get());w.submit(new InferenceWorker.Ticket(100),new Frame(),100,(r,e)->results.incrementAndGet());assertTrue(w.awaitIdle(3000));assertEquals(1,results.get());}finally{w.shutdown();}
    }
}
