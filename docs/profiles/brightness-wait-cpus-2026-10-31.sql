WITH bounds AS (
 SELECT min(CASE WHEN msg GLOB 'BrightnessStage event=RETURN id=1 pid=3638 *' THEN ts END) AS a,
 min(CASE WHEN msg GLOB 'BrightnessStage event=CALLBACK id=1 pid=3638 *' THEN ts END) AS b
 FROM android_logs WHERE tag='WalkBuddyTiming'
), clipped AS (
 SELECT s.cpu,s.utid,t.tid,t.name,max(0,min(s.ts+s.dur,bounds.b)-max(s.ts,bounds.a)) AS ns
 FROM sched s LEFT JOIN thread t USING(utid),bounds
 WHERE s.dur>0 AND s.ts<bounds.b AND s.ts+s.dur>bounds.a
)
SELECT cpu,sum(ns) AS observed_ns,sum(CASE WHEN tid=0 THEN ns ELSE 0 END) AS idle_ns,
 sum(CASE WHEN tid IS NULL THEN ns ELSE 0 END) AS unattributed_ns,
 count(*) AS slices FROM clipped GROUP BY cpu ORDER BY cpu;
