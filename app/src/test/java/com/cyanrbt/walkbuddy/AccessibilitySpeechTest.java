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
}
