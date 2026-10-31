-- One result set, no row truncation, same trace clock for logs and slices.
WITH app_threads AS (
  SELECT t.utid, t.tid, t.name, t.tid = p.pid AS main, t.name GLOB 'WalkBuddy brigh*' AS brightness
  FROM thread t JOIN process p USING (upid)
  WHERE p.name = 'com.cyanrbt.walkbuddy'
), rows AS (
  SELECT 'metric' AS kind, 0 AS ts, 0 AS dur, 0 AS utid, 0 AS tid,
    '' AS thread, 'sched_rows' AS name, count(*) AS value, '' AS detail FROM sched
  UNION ALL
  SELECT 'thread', 0, 0, utid, tid, name, CASE WHEN main THEN 'main' ELSE name END, 0, ''
    FROM app_threads
  UNION ALL
  SELECT 'slice', s.ts, s.dur, t.utid, t.tid, t.name, s.name, 0, ''
    FROM slice s JOIN thread_track tt ON s.track_id = tt.id
    JOIN app_threads t USING (utid)
    WHERE s.name IN ('wb_main:dispatch', 'wb_test:forced_expiry')
      OR ((t.main OR t.brightness OR t.name = 'RenderThread') AND s.dur >= 20000000)
  UNION ALL
  SELECT 'state', s.ts, s.dur, t.utid, t.tid, t.name, s.state, coalesce(s.io_wait, 0),
    coalesce(s.blocked_function, '')
    FROM thread_state s JOIN app_threads t USING (utid)
    WHERE (t.main OR t.brightness OR t.name = 'RenderThread') AND s.dur > 0
  UNION ALL
  SELECT 'cpu', s.ts, s.dur, t.utid, t.tid, t.name, cast(s.cpu AS TEXT), 0, ''
    FROM sched s JOIN app_threads t USING (utid)
    WHERE (t.main OR t.brightness OR t.name = 'RenderThread') AND s.dur > 0
  UNION ALL
  SELECT 'log', ts, 0, utid, 0, '', tag, prio, msg FROM android_logs
    WHERE tag IN ('WalkBuddyTiming', 'WalkBuddyPip', 'WalkBuddyCamera')
  UNION ALL
  SELECT 'stat', 0, 0, 0, 0, '', name, value, severity || '/' || source || '/' || coalesce(cast(idx AS TEXT), '')
    FROM stats WHERE value != 0
)
SELECT * FROM rows ORDER BY ts, kind;
