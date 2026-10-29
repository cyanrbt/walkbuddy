package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

public class InferencePreparationTest {
    private static final class Frame implements AutoCloseable {
        final AtomicInteger closes=new AtomicInteger();
        public void close(){assertEquals(1,closes.incrementAndGet());}
    }
    private static void await(CountDownLatch latch)throws InterruptedException{assertTrue(latch.await(3,TimeUnit.SECONDS));}

    @Test public void preparationOwnsNoImageAndTheFirstFreshFrameUsesTheReadyModel()throws Exception {
        AtomicInteger created=new AtomicInteger(),detected=new AtomicInteger(),closed=new AtomicInteger(),ready=new AtomicInteger(),results=new AtomicInteger();
        AtomicReference<Throwable> error=new AtomicReference<>();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f){detected.incrementAndGet();return "人";}public void close(){closed.incrementAndGet();}
        };});
        try {
            assertFalse(w.ready(t));assertFalse(w.ready(null));
            assertTrue(w.prepare(t,(r,e)->{error.set(e);if(r==null)ready.incrementAndGet();}));await(entered);
            assertTrue(w.busy());assertFalse(w.ready(t));assertEquals(0,detected.get());release.countDown();assertTrue(w.awaitIdle(3000));
            assertNull(error.get());assertTrue(w.ready(t));assertEquals(1,ready.get());assertEquals(0,detected.get());
            assertTrue(w.prepare(t,(r,e)->ready.incrementAndGet()));assertTrue(w.awaitIdle(3000));assertEquals(1,created.get());assertEquals(2,ready.get());
            Frame f=new Frame();assertTrue(w.submit(t,f,100,(r,e)->results.incrementAndGet()));assertTrue(w.awaitIdle(3000));
            assertEquals(1,detected.get());assertEquals(1,results.get());assertEquals(1,f.closes.get());assertEquals(1,created.get());
            w.cancel(t);assertFalse(w.ready(t));assertTrue(w.awaitIdle(3000));assertEquals(1,closed.get());
        } finally {release.countDown();w.shutdown();}
    }

    @Test public void cancellationDuringPreparationClosesAfterFactoryAndNeverPublishesReadiness()throws Exception {
        AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),ready=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f){throw new AssertionError("Preparation must not infer");}public void close(){closed.incrementAndGet();}
        };});
        try {
            w.prepare(t,(r,e)->ready.incrementAndGet());await(entered);w.cancel(t);assertEquals(0,closed.get());assertFalse(w.ready(t));
            release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(1,closed.get());assertEquals(0,ready.get());
            InferenceWorker.Ticket next=new InferenceWorker.Ticket(100);assertTrue(w.prepare(next,(r,e)->ready.incrementAndGet()));assertTrue(w.awaitIdle(3000));
            assertTrue(w.ready(next));assertFalse(w.ready(t));assertEquals(2,created.get());assertEquals(1,ready.get());
            w.cancel(next);assertTrue(w.awaitIdle(3000));assertEquals(2,closed.get());
        } finally {release.countDown();w.shutdown();}
    }

    @Test public void preparationCannotExtendTheOriginalAnalysisDeadline()throws Exception {
        AtomicLong now=new AtomicLong(100);AtomicInteger closed=new AtomicInteger(),ready=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(now::get,()->{entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f){throw new AssertionError("No image");}public void close(){closed.incrementAndGet();}
        };});
        try {
            w.prepare(t,(r,e)->ready.incrementAndGet());await(entered);now.set(1601);t.captureAt=t.displayAt=1601;release.countDown();assertTrue(w.awaitIdle(3000));
            assertEquals(1,closed.get());assertEquals(0,ready.get());assertFalse(w.ready(t));assertEquals(100,t.analysisAt);
            assertFalse(w.prepare(t,(r,e)->fail("Expired owner must not prepare again")));Frame f=new Frame();assertFalse(w.submit(t,f,1601,(r,e)->fail("Expired owner")));assertEquals(1,f.closes.get());
        } finally {release.countDown();w.shutdown();}
    }

    @Test public void preparationFailureIsReportedAndANewPreparationCanRecover()throws Exception {
        AtomicInteger created=new AtomicInteger(),failed=new AtomicInteger(),ready=new AtomicInteger(),closed=new AtomicInteger();
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{if(created.incrementAndGet()==1)throw new IllegalStateException("factory failure");return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f){throw new AssertionError("No image");}public void close(){closed.incrementAndGet();}
        };});
        try {
            assertTrue(w.prepare(t,(r,e)->{if(e!=null)failed.incrementAndGet();}));assertTrue(w.awaitIdle(3000));assertFalse(w.ready(t));assertEquals(1,failed.get());
            assertTrue(w.prepare(t,(r,e)->{if(e==null)ready.incrementAndGet();}));assertTrue(w.awaitIdle(3000));assertEquals(2,created.get());assertEquals(1,ready.get());assertTrue(w.ready(t));
            w.cancel(t);assertTrue(w.awaitIdle(3000));assertEquals(1,closed.get());
        } finally {w.shutdown();}
    }

    @Test public void cancellingQueuedPreparationNeverCreatesAnotherNativeInstance()throws Exception {
        AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),ready=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket active=new InferenceWorker.Ticket(100),queued=new InferenceWorker.Ticket(100);Frame f=new Frame();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame frame)throws Exception{entered.countDown();await(release);return "人";}public void close(){closed.incrementAndGet();}
        };});
        try {
            w.submit(active,f,100,(r,e)->fail("Cancelled result"));await(entered);assertTrue(w.prepare(queued,(r,e)->ready.incrementAndGet()));assertEquals(1,w.pendingCount());
            w.cancel(queued);w.cancel(active);assertEquals(0,w.pendingCount());assertEquals(0,closed.get());assertEquals(0,f.closes.get());
            release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(1,created.get());assertEquals(1,closed.get());assertEquals(0,ready.get());assertEquals(1,f.closes.get());assertFalse(w.ready(queued));
        } finally {release.countDown();w.shutdown();}
    }

    @Test public void geometryDiscardReleasesQueuedImageButKeepsActiveInputAndReadyOwner()throws Exception {
        AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),results=new AtomicInteger(),calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);Frame active=new Frame(),queued=new Frame();
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f)throws Exception{if(calls.incrementAndGet()==1){entered.countDown();await(release);}return "人";}public void close(){closed.incrementAndGet();}
        };});
        try {
            w.submit(t,active,100,(r,e)->fail("Old geometry"));await(entered);w.submit(t,queued,100,(r,e)->fail("Queued old geometry"));w.discard(t);
            assertEquals(1,queued.closes.get());assertEquals(0,active.closes.get());assertFalse(t.cancelled);assertEquals(100,t.analysisAt);assertEquals(0,closed.get());
            release.countDown();assertTrue(w.awaitIdle(3000));assertEquals(1,active.closes.get());assertTrue(w.ready(t));assertEquals(0,closed.get());
            Frame fresh=new Frame();w.submit(t,fresh,100,(r,e)->results.incrementAndGet());assertTrue(w.awaitIdle(3000));assertEquals(1,created.get());assertEquals(1,results.get());assertEquals(1,fresh.closes.get());
            w.cancel(t);assertTrue(w.awaitIdle(3000));assertEquals(1,closed.get());
        } finally {release.countDown();w.shutdown();}
    }

    @Test public void geometryChangeDuringPreparationReusesModelAndOnlyNotifiesTheNewCallback()throws Exception {
        AtomicInteger created=new AtomicInteger(),closed=new AtomicInteger(),ready=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        InferenceWorker.Ticket t=new InferenceWorker.Ticket(100);
        InferenceWorker<Frame,String>w=new InferenceWorker<>(()->100,()->{created.incrementAndGet();entered.countDown();await(release);return new InferenceWorker.Detector<Frame,String>(){
            public String detect(Frame f){throw new AssertionError("No image");}public void close(){closed.incrementAndGet();}
        };});
        try {
            w.prepare(t,(r,e)->fail("Old geometry readiness"));await(entered);w.discard(t);assertTrue(w.prepare(t,(r,e)->ready.incrementAndGet()));
            assertFalse(t.cancelled);assertEquals(100,t.analysisAt);release.countDown();assertTrue(w.awaitIdle(3000));assertTrue(w.ready(t));assertEquals(1,created.get());assertEquals(1,ready.get());assertEquals(0,closed.get());
            w.cancel(t);assertTrue(w.awaitIdle(3000));assertEquals(1,closed.get());
        } finally {release.countDown();w.shutdown();}
    }
}
