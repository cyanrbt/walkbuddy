package com.cyanrbt.walkbuddy;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class HomeStateTest {
    @Test public void advancingFramesDoNotRebuildReportsOrNotifyHome() {
        HomeState home=new HomeState();Object owner=new Object();AtomicInteger updates=new AtomicInteger(),translations=new AtomicInteger();
        home.subscribe(updates::incrementAndGet);home.attach(owner);
        Supplier<String> notice=()->{translations.incrementAndGet();return "检测已暂停";};
        home.publish(owner,SessionState.Phase.LIVE,"实时画面",false,7,notice);
        String first=home.report();int before=updates.get();
        for(int i=0;i<10_000;i++)home.publish(owner,SessionState.Phase.LIVE,new String("实时画面"),false,7,notice);
        assertEquals("实时画面\n识别关着\n检测已暂停",first);
        assertSame(first,home.report());assertEquals(before,updates.get());assertEquals(1,translations.get());
    }
    @Test public void detectionAndNoticeTransitionsPublishExactCurrentText() {
        HomeState home=new HomeState();Object owner=new Object();home.attach(owner);
        home.publish(owner,SessionState.Phase.LIVE,"实时画面",false,0,()->{throw new AssertionError("No notice expected");});
        assertEquals("实时画面\n识别关着",home.report());
        home.publish(owner,SessionState.Phase.LIVE,"实时画面",true,0,null);
        assertEquals("实时画面\n识别开着",home.report());
        home.publish(owner,SessionState.Phase.LIVE,"实时画面",false,1,()->"模型暂不可用");
        assertEquals("实时画面\n识别关着\n模型暂不可用",home.report());
        home.publish(owner,SessionState.Phase.LIVE,"实时画面",false,0,null);
        assertEquals("实时画面\n识别关着",home.report());
    }
    @Test public void newSessionCannotBeOverwrittenByRetiredOwner() {
        HomeState home=new HomeState();Object old=new Object(),next=new Object();home.attach(old);
        home.publish(old,SessionState.Phase.LIVE,"旧相机",true,0,null);
        home.attach(next);home.publish(next,SessionState.Phase.STARTING,"正在启动",false,0,null);
        AtomicInteger changes=new AtomicInteger();home.subscribe(changes::incrementAndGet);String current=home.report();
        home.publish(old,SessionState.Phase.UNAVAILABLE,"旧故障",false,0,null);home.failure(old,"旧故障");
        home.historyChanged(old);home.detach(old);
        assertSame(current,home.report());assertEquals(0,changes.get());
        home.detach(next);assertEquals("已停止；请主动开启",home.report());assertEquals(1,changes.get());
    }
    @Test public void phaseAndHistoryChangesWakeHomeEvenWhenReportTextStaysTheSame() {
        HomeState home=new HomeState();Object owner=new Object();home.attach(owner);
        home.publish(owner,SessionState.Phase.STARTING,"同一文案",false,0,null);
        AtomicInteger changes=new AtomicInteger();home.subscribe(changes::incrementAndGet);String before=home.report();
        home.publish(owner,SessionState.Phase.LIVE,"同一文案",false,0,null);home.historyChanged(owner);
        assertSame(before,home.report());assertEquals(2,changes.get());
    }
    @Test public void firstAttachmentAndDestructionSignalPresenceWithoutAReportDifference() {
        HomeState home=new HomeState();Object owner=new Object();AtomicInteger changes=new AtomicInteger();
        home.subscribe(changes::incrementAndGet);String idle=home.report();home.attach(owner);home.detach(owner);
        assertEquals(idle,home.report());assertEquals(2,changes.get());
    }
    @Test public void unsubscribeDuringDeliveryDoesNotLeakOrDeliverToRetiredActivity() {
        HomeState home=new HomeState();AtomicInteger old=new AtomicInteger(),next=new AtomicInteger();Runnable oldListener=old::incrementAndGet;
        Runnable later=next::incrementAndGet;
        home.subscribe(()->{home.unsubscribe(oldListener);home.subscribe(later);});home.subscribe(oldListener);
        Object owner=new Object();home.attach(owner);assertEquals(0,old.get());assertEquals(0,next.get());
        home.historyChanged(owner);assertEquals(0,old.get());assertEquals(1,next.get());
        home.unsubscribe(later);home.historyChanged(owner);assertEquals(1,next.get());
    }
    @Test public void terminalFailureRemainsVisibleUntilTheNextActualPublication() {
        HomeState home=new HomeState();Object owner=new Object();home.attach(owner);
        home.publish(owner,SessionState.Phase.UNAVAILABLE,"画面过期",false,0,null);home.failure(owner,"画面过期");
        assertEquals("画面过期；提醒已停止",home.report());
        home.publish(owner,SessionState.Phase.STARTING,"正在启动",false,0,null);
        assertEquals("正在启动\n识别关着",home.report());
    }
    @Test public void identicalStoppedRendersKeepTerminalReportWithoutNotificationsOrAllocations() {
        HomeState home=new HomeState();Object owner=new Object();home.attach(owner);
        home.publish(owner,SessionState.Phase.UNAVAILABLE,"画面过期",false,0,null);home.failure(owner,"画面过期");
        String terminal=home.report();AtomicInteger changes=new AtomicInteger();home.subscribe(changes::incrementAndGet);
        for(int i=0;i<10_000;i++)home.publish(owner,SessionState.Phase.UNAVAILABLE,new String("画面过期"),false,0,
            ()->{throw new AssertionError("Unchanged stopped state should not translate notices");});
        assertSame(terminal,home.report());assertEquals("画面过期；提醒已停止",home.report());assertEquals(0,changes.get());
    }
    @Test public void nextPhaseClearsTerminalReportEvenWhenItsReasonIsUnchanged() {
        HomeState home=new HomeState();Object owner=new Object();home.attach(owner);
        home.publish(owner,SessionState.Phase.UNAVAILABLE,"同一文案",false,0,null);home.failure(owner,"同一文案");
        AtomicInteger changes=new AtomicInteger();home.subscribe(changes::incrementAndGet);
        home.publish(owner,SessionState.Phase.STARTING,"同一文案",false,0,null);
        assertEquals("同一文案\n识别关着",home.report());assertEquals(1,changes.get());
    }
}
