package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionDiagnosticsTest {
    private SessionState live() {
        SessionState state=new SessionState();state.start(100);state.capture(1,200);state.display(220);state.promote(220);
        state.recognition=true;state.analysisAt=240;return state;
    }
    private SessionDiagnostics.Snapshot snapshot(String category,SessionState state) {
        return new SessionDiagnostics.Snapshot(category,"测试提示",state,400,true,true,true,
            new SessionDiagnostics.Pipeline(true,true,false,120,5,6,7));
    }
    private String report(SessionDiagnostics diagnostics) {
        return diagnostics.report("0.4.28",40,"测试厂商","测试设备","15",35);
    }
    @Test public void incidentSurvivesTheSessionAndResourceReset() {
        SessionState state=live();SessionDiagnostics diagnostics=new SessionDiagnostics();
        diagnostics.preview(snapshot("GENERAL",state));state.unavailable("后续停止");
        SessionDiagnostics.Snapshot saved=diagnostics.preview();
        assertEquals(SessionState.Phase.LIVE,saved.phase);assertTrue(saved.recognition);assertTrue(saved.sound);
        assertEquals(200,saved.captureAge);assertEquals(180,saved.displayAge);assertEquals(160,saved.analysisAge);
        String text=report(diagnostics);assertTrue(text.contains("检测：开"));assertTrue(text.contains("复制(ms)：6"));
    }
    @Test public void lifecycleStopDoesNotOverwriteTheFirstPreviewIncident() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();SessionDiagnostics.Snapshot first=snapshot("DARK",live());
        diagnostics.preview(first);diagnostics.preview(snapshot("HIDDEN",new SessionState()));
        assertSame(first,diagnostics.preview());assertTrue(report(diagnostics).contains("分类：DARK"));
        assertFalse(report(diagnostics).contains("分类：HIDDEN"));
    }
    @Test public void detectionIncidentAndLaterPreviewIncidentRemainSeparate() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();
        SessionDiagnostics.Snapshot detection=snapshot("DETECTION_TIMEOUT",live());
        diagnostics.detection(detection);diagnostics.preview(snapshot("GENERAL",live()));
        diagnostics.detection(snapshot("LATE_CALLBACK",live()));
        assertSame(detection,diagnostics.detection());String text=report(diagnostics);
        assertTrue(text.indexOf("本次检测首次暂停")<text.indexOf("本次预览首次停止"));
        assertTrue(text.contains("DETECTION_TIMEOUT"));assertFalse(text.contains("LATE_CALLBACK"));
    }
    @Test public void aLateDetectionCannotCreateAnIncidentAfterPreviewStop() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();diagnostics.preview(snapshot("LOCKED",live()));
        diagnostics.detection(snapshot("DETECTION_ERROR",live()));assertNull(diagnostics.detection());
    }
    @Test public void acceptedNewObservationClearsBothAndEmptyReportCannotBeCopied() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();diagnostics.detection(snapshot("DETECTION_ERROR",live()));
        diagnostics.preview(snapshot("GENERAL",live()));diagnostics.clear();
        assertFalse(diagnostics.available());assertNull(diagnostics.preview());assertNull(diagnostics.detection());
        assertEquals("",report(diagnostics));
    }
    @Test public void detectionRetryOnlyRetiresDetectionEvidence() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();diagnostics.detection(snapshot("DETECTION_ERROR",live()));
        diagnostics.clearDetection();assertFalse(diagnostics.available());
        SessionDiagnostics.Snapshot preview=snapshot("CAMERA_BUSY",live());diagnostics.preview(preview);
        diagnostics.clearDetection();assertSame(preview,diagnostics.preview());assertTrue(diagnostics.available());
    }
    @Test public void missingAndFutureTimingAreReportedAsUnavailableAndZeroDurationsRemainValid() {
        SessionState state=new SessionState();state.captureAt=500;
        SessionDiagnostics diagnostics=new SessionDiagnostics();
        diagnostics.preview(new SessionDiagnostics.Snapshot("PERMISSION","权限提示",state,400,false,false,false,
            new SessionDiagnostics.Pipeline(false,false,false,-1,0,-1,0)));
        String text=report(diagnostics);assertTrue(text.contains("采集帧龄(ms)：未取得"));
        assertTrue(text.contains("读回排队(ms)：0"));assertTrue(text.contains("结果交付(ms)：0"));
        assertFalse(text.contains("：-1"));assertEquals(-1,SessionDiagnostics.age(400,500));
        assertEquals(-1,SessionDiagnostics.age(400,0));assertEquals(0,SessionDiagnostics.age(400,400));
    }
    @Test public void copiedTextHasBoundedSingleLineMetadataAndNoStateReferences() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();String huge="x".repeat(10000);
        SessionState state=live();
        diagnostics.preview(new SessionDiagnostics.Snapshot("TYPE\nsecond",huge,state,400,false,false,false,
            new SessionDiagnostics.Pipeline(false,false,false,-1,-1,-1,-1)));
        String text=diagnostics.report("v\nnext",40,huge,"model\r\nnext",null,35);
        assertTrue(text.length()<1800);assertTrue(text.contains("TYPE second"));assertFalse(text.contains("TYPE\nsecond"));
        state.recognition=false;state.analysisAt=0;assertTrue(report(diagnostics).contains("检测：开"));
    }
    @Test public void pageRecreationPreservesTheFrozenReportAndDoesNotGrowIt() {
        SessionDiagnostics old=new SessionDiagnostics();old.preview(snapshot("DARK",live()));
        SessionDiagnostics restored=new SessionDiagnostics();restored.restore(report(old));
        assertTrue(restored.available());assertTrue(report(restored).contains("保留记录：页面重建前的信息"));
        assertTrue(report(restored).contains("分类：DARK"));String first=report(restored);
        for(int i=0;i<5;i++) {
            SessionDiagnostics next=new SessionDiagnostics();next.restore(report(restored));restored=next;
            assertEquals(first,report(restored));
        }
        restored.preview(snapshot("HIDDEN",live()));assertEquals(first,report(restored));
        restored.clear();assertEquals("",report(restored));
    }
    @Test public void invalidRestorationCannotCreateOrOverwriteEvidence() {
        SessionDiagnostics diagnostics=new SessionDiagnostics();
        for(String invalid:new String[]{null,"", "unrelated report", "WalkBuddy 问题信息\n"+"x".repeat(5000),"WalkBuddy 问题信息\n\0"})
            diagnostics.restore(invalid);
        assertFalse(diagnostics.available());
        diagnostics.preview(snapshot("CAMERA_BUSY",live()));String first=report(diagnostics);
        diagnostics.restore("WalkBuddy 问题信息\n替换内容");assertEquals(first,report(diagnostics));
    }
}
