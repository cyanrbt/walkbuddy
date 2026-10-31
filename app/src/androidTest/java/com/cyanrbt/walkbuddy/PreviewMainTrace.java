package com.cyanrbt.walkbuddy;

import android.app.Instrumentation;
import android.os.Looper;
import android.os.Trace;
import android.util.Printer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Explicit test-only dispatch observation. Never installed by the application. */
final class PreviewMainTrace implements Printer, AutoCloseable {
    private final Instrumentation runner;
    private boolean open;
    private boolean closing;
    private final CountDownLatch closed=new CountDownLatch(1);

    PreviewMainTrace(Instrumentation runner) {
        this.runner=runner;
        runner.runOnMainSync(()->Looper.getMainLooper().setMessageLogging(this));
    }

    @Override public void println(String line) {
        if(line.startsWith(">>>>>")) {
            if(open)Trace.endSection();
            Trace.beginSection("wb_main:dispatch");open=true;
        } else if(line.startsWith("<<<<<") && open) {
            Trace.endSection();open=false;
            if(closing){Looper.getMainLooper().setMessageLogging(null);closed.countDown();}
        }
        // Installing inside a dispatch can expose its end without its beginning.
    }

    @Override public void close() {
        // End at the dispatch boundary, after any nested framework trace sections.
        runner.runOnMainSync(()->closing=true);
        try {
            if(!closed.await(5,TimeUnit.SECONDS))throw new AssertionError("Main trace observer did not close");
        } catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
    }
}
