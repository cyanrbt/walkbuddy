package com.cyanrbt.walkbuddy;

/** Two immutable incident snapshots per observation; no frame, object list, or worker is retained. */
final class SessionDiagnostics {
    static final class Pipeline {
        final boolean ready,result,busy;
        final long sampleAge,queueMs,copyMs,deliveryMs;
        Pipeline(boolean ready,boolean result,boolean busy,long sampleAge,long queueMs,long copyMs,long deliveryMs) {
            this.ready=ready;this.result=result;this.busy=busy;this.sampleAge=sampleAge;
            this.queueMs=queueMs;this.copyMs=copyMs;this.deliveryMs=deliveryMs;
        }
    }
    static final class Snapshot {
        final String category,message;
        final SessionState.Phase phase;
        final boolean depth,pip,recognition,sound;
        final long captureAge,displayAge,analysisAge;
        final Pipeline pipeline;
        Snapshot(String category,String message,SessionState state,long now,boolean depth,boolean pip,boolean sound,Pipeline pipeline) {
            this.category=line(category,40);this.message=line(message,240);this.phase=state.phase;
            this.depth=depth;this.pip=pip;this.recognition=state.recognition;this.sound=sound;
            captureAge=age(now,state.captureAt);displayAge=age(now,state.displayAt);analysisAge=age(now,state.analysisAt);
            this.pipeline=pipeline;
        }
    }
    private static final String HEADER="WalkBuddy 问题信息\n",RESTORED="保留记录：页面重建前的信息\n";
    private Snapshot preview,detection;
    private String restored;
    void preview(Snapshot snapshot){if(restored==null && preview==null)preview=snapshot;}
    void detection(Snapshot snapshot){if(restored==null && preview==null && detection==null)detection=snapshot;}
    void clear(){preview=detection=null;restored=null;}
    void clearDetection(){detection=null;}
    boolean available(){return restored!=null || preview!=null || detection!=null;}
    Snapshot preview(){return preview;}
    Snapshot detection(){return detection;}
    void restore(String report) {
        if(available() || report==null || report.length()>4096 || !report.startsWith(HEADER)
            || !report.contains("\n版本：") || !report.contains("\n分类：") || report.indexOf('\0')>=0)return;
        String normalized=report.startsWith(HEADER+RESTORED)?report:HEADER+RESTORED+report.substring(HEADER.length());
        if(normalized.length()<=4096)restored=normalized;
    }

    String report(String version,int code,String manufacturer,String model,String android,int api) {
        if(!available())return "";
        if(restored!=null)return restored;
        StringBuilder text=new StringBuilder(HEADER).append("版本：").append(line(version,32)).append(" / ").append(code)
            .append("\n设备：").append(line(manufacturer,96)).append(" / ").append(line(model,96))
            .append("\nAndroid：").append(line(android,32)).append(" / API ").append(api).append('\n');
        if(detection!=null)append(text,"本次检测首次暂停",detection);
        if(preview!=null)append(text,"本次预览首次停止",preview);
        return text.append("\n时序为事件发生时的回调与模型记录，非相机端到端延迟。\n").toString();
    }
    private static void append(StringBuilder text,String title,Snapshot s) {
        text.append('\n').append(title).append('\n').append("分类：").append(s.category).append("\n提示：").append(s.message)
            .append("\n触发阶段：").append(s.phase).append(" / ").append(s.depth?"空间增强":"基础模式")
            .append("\n画中画：").append(yes(s.pip)).append("；检测：").append(setting(s.recognition)).append("；声音：").append(setting(s.sound))
            .append("\n采集帧龄(ms)：").append(metric(s.captureAge)).append("；显示帧龄(ms)：").append(metric(s.displayAge))
            .append("；分析龄(ms)：").append(metric(s.analysisAge))
            .append("\n模型就绪：").append(yes(s.pipeline.ready)).append("；已有结果：").append(yes(s.pipeline.result))
            .append("；工作线程忙：").append(yes(s.pipeline.busy))
            .append("\n取样龄(ms)：").append(metric(s.pipeline.sampleAge)).append("；读回排队(ms)：").append(metric(s.pipeline.queueMs))
            .append("；复制(ms)：").append(metric(s.pipeline.copyMs)).append("；结果交付(ms)：").append(metric(s.pipeline.deliveryMs)).append('\n');
    }
    static long age(long now,long at){return at>0 && now>=at?now-at:-1;}
    private static String metric(long value){return value<0?"未取得":Long.toString(value);}
    private static String yes(boolean value){return value?"是":"否";}
    private static String setting(boolean value){return value?"开":"关";}
    private static String line(String value,int limit) {
        if(value==null)return "未取得";
        String clean=value.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim();
        if(clean.isEmpty())return "未取得";
        return clean.length()>limit?clean.substring(0,limit)+"…":clean;
    }
}
