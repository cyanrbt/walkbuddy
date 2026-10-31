WITH app_threads AS (
 SELECT t.utid,t.tid,t.name,t.upid,p.pid
 FROM thread t JOIN process p USING(upid) WHERE p.pid=3638
), bounds AS (
 SELECT
  min(CASE WHEN msg GLOB 'BrightnessStage event=RETURN id=1 pid=3638 *' THEN ts END) AS a,
  min(CASE WHEN msg GLOB 'BrightnessStage event=CALLBACK id=1 pid=3638 *' THEN ts END) AS b
 FROM android_logs WHERE tag='WalkBuddyTiming'
), rows AS (
 SELECT 'log' AS kind,l.ts,0 AS dur,l.utid,t.tid,t.name AS thread,l.tag AS name,l.msg AS detail
 FROM android_logs l LEFT JOIN thread t USING(utid)
 WHERE l.tag='WalkBuddyTiming' AND l.msg GLOB 'BrightnessStage * id=1 pid=3638 *'
 UNION ALL
 SELECT 'slice',s.ts,s.dur,t.utid,t.tid,t.name,s.name,''
 FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN app_threads t USING(utid),bounds
 WHERE (t.tid=t.pid OR t.name='RenderThread' OR t.name GLOB 'WalkBuddy brigh*')
  AND s.dur>0 AND s.ts<bounds.b AND s.ts+s.dur>bounds.a
 UNION ALL
 SELECT 'state',s.ts,s.dur,t.utid,t.tid,t.name,s.state,coalesce(s.blocked_function,'')
 FROM thread_state s JOIN app_threads t USING(utid),bounds
 WHERE (t.tid=t.pid OR t.name='RenderThread' OR t.name GLOB 'WalkBuddy brigh*')
  AND s.dur>0 AND s.ts<bounds.b AND s.ts+s.dur>bounds.a
 UNION ALL
 SELECT 'cpu',bounds.a,sum(max(0,min(s.ts+s.dur,bounds.b)-max(s.ts,bounds.a))),t.utid,t.tid,t.name,'',''
 FROM sched s JOIN app_threads t USING(utid),bounds
 WHERE s.dur>0 AND s.ts<bounds.b AND s.ts+s.dur>bounds.a GROUP BY t.utid
 UNION ALL
 SELECT 'process_cpu',bounds.a,sum(max(0,min(s.ts+s.dur,bounds.b)-max(s.ts,bounds.a))),0,p.pid,p.name,'',''
 FROM sched s JOIN thread t USING(utid) JOIN process p USING(upid),bounds
 WHERE s.dur>0 AND s.ts<bounds.b AND s.ts+s.dur>bounds.a GROUP BY p.upid
)
SELECT * FROM rows ORDER BY kind,ts,dur DESC;
