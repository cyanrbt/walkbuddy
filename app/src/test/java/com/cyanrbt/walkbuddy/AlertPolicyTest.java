package com.cyanrbt.walkbuddy;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;

public class AlertPolicyTest {
    private Detection person(float left,float right){return new Detection("空间障碍",1,left,0.2f,right,0.8f,Detection.Risk.CAUTION,1.5f,null);}
    private AlertPolicy.Decision update(AlertPolicy policy,long time,Detection... detections){return policy.update(Arrays.asList(detections),time);}
    @Test public void requiresSameTargetAndSuppressesRepeats() {
        AlertPolicy p=new AlertPolicy();Detection a=person(0.3f,0.6f);
        assertFalse(update(p,100,a).alert);assertTrue(update(p,600,a).alert);
        assertFalse(update(p,1100,a).alert);
        for(long time=1600;time<8600;time+=500)assertFalse(update(p,time,a).alert);
        assertTrue(update(p,8600,a).alert);
    }
    @Test public void differentPeopleDoNotConfirmEachOther() {
        AlertPolicy p=new AlertPolicy();
        assertFalse(update(p,100,person(0.1f,0.3f)).alert);
        assertFalse(update(p,600,person(0.65f,0.85f)).alert);
        assertTrue(update(p,1100,person(0.64f,0.84f)).alert);
    }
    @Test public void emptyOrStaleResultBreaksConfirmation() {
        AlertPolicy p=new AlertPolicy();Detection a=person(0.3f,0.6f);
        update(p,100,a);update(p,600);
        assertFalse(update(p,1100,a).alert);assertFalse(update(p,2400,a).alert);
        assertTrue(update(p,2900,a).alert);
        p.clear();assertFalse(update(p,7000,a).alert);
    }
    @Test public void weakOrPeripheralDetectionsRemainVisibleWithoutAlert() {
        AlertPolicy p=new AlertPolicy();Detection a=new Detection("人",0.8f,0.01f,0.2f,0.2f,0.8f);
        update(p,100,a);assertFalse(update(p,600,a).alert);
        Detection weak=new Detection("人",0.4f,0.3f,0.2f,0.6f,0.8f);
        update(p,1100,weak);assertFalse(update(p,1600,weak).alert);
        assertTrue(update(p,2100,weak).message.contains("已捕获 1 处"));
    }
    @Test public void matchDoesNotDependOnDetectorOrdering() {
        AlertPolicy p=new AlertPolicy();Detection a=person(0.1f,0.35f),b=person(0.65f,0.9f);
        update(p,100,a,b);assertTrue(update(p,600,b,a).alert);
        assertFalse(update(p,1100,a,b).alert);
    }
    @Test public void labelChangeDoesNotConfirm() {
        AlertPolicy p=new AlertPolicy();update(p,100,person(0.3f,0.6f));
        assertFalse(update(p,600,new Detection("汽车",0.8f,0.3f,0.2f,0.6f,0.8f)).alert);
    }
    @Test public void unpaddingAndClippingPreserveImageCoordinates() {
        Detection d=Detection.fromModel("人",0.8f,new float[]{0.25f,0.25f,0.75f,0.75f},240,320,40,0);
        assertNotNull(d);assertEquals(1f/6,d.left,0.0001f);assertEquals(5f/6,d.right,0.0001f);
        assertEquals(0.25f,d.top,0.0001f);assertEquals(0.75f,d.bottom,0.0001f);
        assertNull(Detection.fromModel("人",0.8f,new float[]{0,0,1,0.1f},240,320,40,0));
        Detection full=Detection.fromModel("人",0.8f,new float[]{0,0,1,1},320,240,0,40);
        assertEquals(0,full.top,0);assertEquals(1,full.bottom,0);
    }
    @Test public void uncertaintyPrecedesLongObjectDetailsAfterConfirmation() {
        String label="很长的模型类别名称仍应保留完整内容";
        Detection object=Detection.fromModel(label,.8f,new float[]{.1f,.3f,.9f,.7f},320,320,0,0);
        AlertPolicy policy=new AlertPolicy();
        AlertPolicy.Decision candidate=update(policy,100,object);
        assertTrue(candidate.message.startsWith("灰色不代表安全"));assertFalse(candidate.alert);
        AlertPolicy.Decision confirmed=update(policy,600,object);
        assertTrue(confirmed.message.startsWith("未测距 · "));
        assertTrue(confirmed.message.contains(label));assertTrue(confirmed.message.endsWith(object.position()));
        assertTrue(confirmed.alert);assertEquals(Detection.Risk.CAUTION,confirmed.detections.get(0).risk);
        assertTrue(Float.isNaN(confirmed.detections.get(0).meters));
        assertTrue(update(policy,1100).message.startsWith("不代表可通行"));
    }
    @Test public void measuredDistancePrecedesDetailsWithoutChangingRiskEscalation() {
        AlertPolicy policy=new AlertPolicy();Detection caution=person(.3f,.6f);
        update(policy,100,caution);
        AlertPolicy.Decision yellow=update(policy,600,caution);
        assertTrue(yellow.message.startsWith("约 1.5 米 · "));assertTrue(yellow.message.contains("前方可能受阻"));
        Detection near=new Detection("空间障碍",1,.3f,.2f,.6f,.8f,Detection.Risk.NEAR,.7f,null);
        AlertPolicy.Decision firstNear=update(policy,1100,near);
        assertEquals(Detection.Risk.CAUTION,firstNear.detections.get(0).risk);assertFalse(firstNear.alert);
        AlertPolicy.Decision red=update(policy,1600,near);
        assertTrue(red.message.startsWith("约 0.7 米 · "));assertTrue(red.message.contains("请及时注意"));
        assertTrue(red.message.endsWith(near.position()));assertTrue(red.alert);
        assertEquals(Detection.Risk.NEAR,red.detections.get(0).risk);
    }
}
