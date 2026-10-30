package com.cyanrbt.walkbuddy;

import java.util.regex.Pattern;

/** Test-only readiness check for the pinned Android 15/16 emulator input dumps. */
final class InputWindowState {
    static boolean touchable(String snapshot,String component,int displayId,int uid) {
        Pattern viewport=Pattern.compile("(?m)^\\s*Viewport [^:\\n]+: displayId="+displayId+",[^\\n]*isActive=\\[1\\]");
        if(!viewport.matcher(snapshot).find())return false;
        int dispatcher=snapshot.indexOf("Input Dispatcher State:");
        if(dispatcher<0)return false;
        for(String line:snapshot.substring(dispatcher).split("\n")) {
            if(!line.contains(": name=") || !line.contains(" "+component+",")
                || !line.contains("displayId="+displayId+",") || !line.contains("ownerUid="+uid+","))continue;
            int start=line.indexOf("inputConfig="),end=line.indexOf(", alpha=",start);
            if(start<0 || end<0)continue;
            String flags=line.substring(start,end);
            if(!flags.contains("NOT_TOUCHABLE") && !flags.contains("NOT_VISIBLE")
                && !flags.contains("NO_INPUT_CHANNEL") && !flags.contains("PAUSE_DISPATCHING"))return true;
        }
        return false;
    }

    static int verifyWakeFixture(String inactive) {
        String component="com.cyanrbt.walkbuddy/com.cyanrbt.walkbuddy.PreviewActivity";
        String active=inactive.replace("isActive=[0]","isActive=[1]");
        String ready=active.replace("inputConfig=NOT_TOUCHABLE","inputConfig=0x0");
        if(touchable(inactive,component,0,10216) || touchable(active,component,0,10216)
            || touchable(inactive.replace("inputConfig=NOT_TOUCHABLE","inputConfig=0x0"),component,0,10216)
            || !touchable(ready,component,0,10216) || touchable(ready,component,0,10180)
            || touchable(ready,component,1,10216) || touchable(ready,component+"Other",0,10216)
            || touchable("",component,0,10216)
            || touchable(ready.replace("inputConfig=0x0","inputConfig=NOT_VISIBLE"),component,0,10216)
            || touchable(ready.replace("inputConfig=0x0","inputConfig=NO_INPUT_CHANNEL"),component,0,10216))
            throw new AssertionError("Wake input readiness accepted an inactive/unavailable/wrong target or rejected a ready fixture");
        return 10;
    }
}
