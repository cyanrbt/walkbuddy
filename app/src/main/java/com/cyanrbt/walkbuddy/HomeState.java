package com.cyanrbt.walkbuddy;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Supplier;

/** Main-thread session publications; report alone may be read by diagnostics off-thread. */
final class HomeState {
    private static final String STOPPED="已停止；请主动开启";
    private final LinkedHashSet<Runnable> listeners=new LinkedHashSet<>();
    private Object owner;
    private SessionState.Phase phase;
    private String reason;
    private boolean recognition;
    private int notice;
    private volatile String report=STOPPED;

    String report(){return report;}
    void subscribe(Runnable listener){listeners.add(listener);}
    void unsubscribe(Runnable listener){listeners.remove(listener);}
    void attach(Object next) {
        owner=Objects.requireNonNull(next);phase=null;reason=null;report=STOPPED;
        changed();
    }
    void publish(Object source,SessionState.Phase nextPhase,String nextReason,boolean nextRecognition,
                 int nextNotice,Supplier<String> noticeText) {
        if(source!=owner)return;
        boolean textChanged=!Objects.equals(reason,nextReason) || recognition!=nextRecognition || notice!=nextNotice;
        if(!textChanged && phase==nextPhase)return;
        phase=nextPhase;
        if(textChanged) {
            reason=nextReason;recognition=nextRecognition;notice=nextNotice;
            report=reason+"\n"+(recognition?"识别开着":"识别关着")+(notice==0?"":"\n"+noticeText.get());
        }
        changed();
    }
    void failure(Object source,String message) {
        if(source!=owner)return;
        String next=message+"；提醒已停止";
        if(next.equals(report))return;
        report=next;reason=null;changed();
    }
    void historyChanged(Object source){if(source==owner)changed();}
    void detach(Object source) {
        if(source!=owner)return;
        owner=null;phase=null;reason=null;report=STOPPED;changed();
    }
    private void changed() {
        for(Runnable listener:listeners.toArray(new Runnable[0]))
            if(listeners.contains(listener))listener.run();
    }
}
