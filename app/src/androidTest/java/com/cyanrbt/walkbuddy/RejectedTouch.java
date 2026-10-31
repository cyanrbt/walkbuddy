package com.cyanrbt.walkbuddy;

/** Test-only evidence for a DOWN that InputDispatcher delivered to no window. */
final class RejectedTouch {
    static boolean pausedDown(String before,String after,long eventAtMs,int pid,String component) {
        if(eventAtMs<0 || eventAtMs>Long.MAX_VALUE/1_000_000 || pid<=0 || component.isEmpty())return false;
        String token="eventTime="+(eventAtMs*1_000_000)+",";
        // A buffer replay or ambiguous repeated event must never authorize another gesture.
        if(before.contains(token))return false;
        String[] lines=after.split("\n");int matching=0;boolean proved=false;
        for(int index=0;index<lines.length;index++) {
            String line=lines[index];
            if(!line.contains("Dropping event because no targets were found: MotionEvent(")
                || !line.contains(token) || !line.contains("action=DOWN,"))continue;
            matching++;
            boolean paused=false,failed=false;
            for(int i=index-1;i>=Math.max(0,index-6);i--) {
                if(lines[i].contains("Input event injection from pid ") || lines[i].contains("MotionEvent("))break;
                paused|=lines[i].contains(" "+component+" because it is paused");
            }
            for(int i=index+1;i<Math.min(lines.length,index+5);i++) {
                if(lines[i].contains("MotionEvent("))break;
                if(lines[i].contains("Input event injection from pid ")) {
                    failed=lines[i].contains("Input event injection from pid "+pid+" failed.");break;
                }
            }
            proved|=paused && failed;
        }
        return matching==1 && proved;
    }

    static boolean mayRetry(int attempt,boolean rejected,boolean unchanged,long remainingMs) {
        return attempt==1 && rejected && unchanged && remainingMs>=500;
    }

    static boolean sameInactiveObservation(boolean inactive,boolean ownerStable,long previousStart,long currentStart,
                                           Object previousIncident,Object currentIncident) {
        return inactive && ownerStable && previousStart==currentStart && previousIncident==currentIncident;
    }

    static int verifyFixture(String fixture) {
        String component="com.cyanrbt.walkbuddy/com.cyanrbt.walkbuddy.PreviewActivity";
        String noPaused=fixture.replace(" because it is paused"," because it is ready");
        String noDrop=fixture.replace("no targets were found","a target was found");
        String noCaller=fixture.replace("from pid 2162 failed.","from pid 2163 failed.");
        String firstWrongCaller=fixture.replaceFirst("from pid 2162 failed\\.","from pid 2163 failed.");
        String priorPaused="InputDispatcher: Not sending touch event to old "+component+" because it is paused\n"
            +"InputManager: Input event injection from pid 2162 failed.\n"+noPaused;
        Object incident=new Object();
        boolean[] checks={
            pausedDown("",fixture,54407,2162,component),
            !pausedDown(fixture,fixture,54407,2162,component),
            !pausedDown("",fixture,54408,2162,component),
            !pausedDown("",fixture,54407,2163,component),
            !pausedDown("",fixture,54407,2162,component+"Other"),
            !pausedDown("",fixture.replace("action=DOWN,","action=UP,"),54407,2162,component),
            !pausedDown("",noPaused,54407,2162,component),
            !pausedDown("",noDrop,54407,2162,component),
            !pausedDown("",noCaller,54407,2162,component),
            !pausedDown("",fixture+fixture,54407,2162,component),
            !pausedDown("","",54407,2162,component),
            !pausedDown("",fixture.replace("eventTime=54407000000,","eventTime=544070000000,"),54407,2162,component),
            !pausedDown("",fixture,-1,2162,component),
            !pausedDown("",fixture,Long.MAX_VALUE,2162,component),
            !pausedDown("",firstWrongCaller,54407,2162,component),
            !pausedDown("",priorPaused,54407,2162,component),
            mayRetry(1,true,true,500),
            !mayRetry(2,true,true,5000),
            !mayRetry(0,true,true,5000),
            !mayRetry(1,false,true,5000),
            !mayRetry(1,true,false,5000),
            !mayRetry(1,true,true,499),
            sameInactiveObservation(true,true,1000,1000,incident,incident),
            !sameInactiveObservation(false,true,1000,1000,incident,incident),
            !sameInactiveObservation(true,false,1000,1000,incident,incident),
            !sameInactiveObservation(true,true,1000,2000,incident,incident),
            !sameInactiveObservation(true,true,1000,1000,incident,new Object())
        };
        for(int i=0;i<checks.length;i++)if(!checks[i])throw new AssertionError("Rejected touch fixture case="+i);
        return checks.length;
    }
}
