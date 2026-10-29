package com.cyanrbt.walkbuddy;

import org.junit.Test;

import static org.junit.Assert.*;

public class AccessibilitySpeechTest {
    @Test public void sameTierStaysQuietWithoutAlert() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        assertNull(speech.update("已捕获 2 处目标", Detection.Risk.CAPTURED, false, 100));
        assertNull(speech.update("已捕获 2 处目标", Detection.Risk.CAPTURED, false, 200));
    }

    @Test public void returningToCautionDoesNotRespeak() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        assertNull(speech.update("灰", Detection.Risk.CAPTURED, false, 100));
        assertEquals("黄", speech.update("黄", Detection.Risk.CAUTION, false, 200));
        assertNull(speech.update("灰", Detection.Risk.CAPTURED, false, 300));
        assertNull(speech.update("黄", Detection.Risk.CAUTION, false, 400));
    }

    @Test public void escalationToCautionIsSpokenOnce() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        speech.update("灰色", Detection.Risk.CAPTURED, false, 100);
        assertEquals("画面关注 · 人 · 未测距", speech.update("画面关注 · 人 · 未测距", Detection.Risk.CAUTION, false, 200));
        assertNull(speech.update("画面关注 · 人 · 未测距", Detection.Risk.CAUTION, false, 300));
    }

    @Test public void alertIsSpokenEvenInSameTierAfterQuiet() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        speech.update("前方可能受阻", Detection.Risk.CAUTION, true, 1000);
        assertEquals("前方可能受阻", speech.update("前方可能受阻", Detection.Risk.CAUTION, true, 3500));
    }

    @Test public void nearEscalationInterruptsSoonerThanCaution() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        speech.update("关注", Detection.Risk.CAUTION, true, 1000);
        assertEquals("请及时注意", speech.update("请及时注意", Detection.Risk.NEAR, true, 2600));
    }

    @Test public void clearResetsTierHistory() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        speech.update("红", Detection.Risk.NEAR, true, 1000);
        speech.clear();
        assertEquals("红", speech.update("红", Detection.Risk.NEAR, true, 1100));
    }

    @Test public void emptyMessageNeverSpeaks() {
        AccessibilitySpeech speech = new AccessibilitySpeech();
        assertNull(speech.update("", Detection.Risk.NEAR, true, 100));
        assertNull(speech.update(null, Detection.Risk.NEAR, true, 100));
    }
    @Test public void nearUpgradeIsNotLostInsideCautionCooldown() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        assertEquals("黄",s.update("黄",Detection.Risk.CAUTION,true,1000));
        assertEquals("红",s.update("红",Detection.Risk.NEAR,false,1001));
        assertNull(s.update("红",Detection.Risk.NEAR,false,1002));
    }
    @Test public void suppressedReminderWaitsAndUsesLatestCurrentMessage() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        s.update("首次",Detection.Risk.CAUTION,true,1000);
        assertNull(s.update("旧距离",Detection.Risk.CAUTION,true,1100));
        assertNull(s.update("中途距离",Detection.Risk.CAUTION,false,2499));
        assertEquals("当前距离",s.update("当前距离",Detection.Risk.CAUTION,false,2500));
        assertNull(s.update("当前距离",Detection.Risk.CAUTION,false,2501));
    }
    @Test public void expiredTargetCancelsPendingReminder() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        s.update("首次",Detection.Risk.CAUTION,true,1000);
        s.update("旧目标",Detection.Risk.CAUTION,true,1100);
        assertNull(s.update("没有目标",Detection.Risk.CAPTURED,false,1200));
        assertNull(s.update("没有目标",Detection.Risk.CAPTURED,false,5000));
        assertEquals("新目标",s.update("新目标",Detection.Risk.CAUTION,false,6000));
    }
    @Test public void downgradeCancelsQueuedNearReminder() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        s.update("红",Detection.Risk.NEAR,true,1000);
        assertNull(s.update("旧红",Detection.Risk.NEAR,true,1200));
        assertNull(s.update("黄",Detection.Risk.CAUTION,false,1300));
        assertNull(s.update("黄",Detection.Risk.CAUTION,false,2700));
        assertEquals("新黄",s.update("新黄",Detection.Risk.CAUTION,true,3000));
    }
    @Test public void zeroTimestampDoesNotDisableCooldown() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        assertEquals("首次",s.update("首次",Detection.Risk.CAUTION,true,0));
        assertNull(s.update("稍后",Detection.Risk.CAUTION,true,1));
        assertEquals("稍后",s.update("稍后",Detection.Risk.CAUTION,false,1500));
    }
    @Test public void clearDropsQueuedSpeechBeforeNewSession() {
        AccessibilitySpeech s=new AccessibilitySpeech();
        s.update("红",Detection.Risk.NEAR,true,1000);s.update("排队红",Detection.Risk.NEAR,true,1100);
        s.clear();
        assertNull(s.update("无目标",Detection.Risk.CAPTURED,false,1200));
        assertEquals("新黄",s.update("新黄",Detection.Risk.CAUTION,false,1300));
    }
}
