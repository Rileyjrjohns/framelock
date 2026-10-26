-- Duration of the app's trace sections (ADR-009), per thread: count, mean, median and max in ms.
WITH s AS (
  SELECT s.name AS section, t.name AS thread, s.dur
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t USING (utid)
  JOIN process p USING (upid)
  WHERE p.name LIKE 'dev.framelock%'
    AND s.name IN ('CAPTURE', 'PREPROCESS', 'INFERENCE', 'POSTPROCESS', 'RENDER')
),
ranked AS (
  SELECT section, thread, dur,
    ROW_NUMBER() OVER (PARTITION BY section, thread ORDER BY dur) AS rk,
    COUNT(*) OVER (PARTITION BY section, thread) AS n
  FROM s
)
SELECT section, thread, n,
  ROUND(AVG(dur) / 1e6, 2) AS mean_ms,
  ROUND(MAX(CASE WHEN rk = (n + 1) / 2 THEN dur END) / 1e6, 2) AS median_ms,
  ROUND(MAX(dur) / 1e6, 2) AS max_ms
FROM ranked
GROUP BY section, thread
ORDER BY mean_ms DESC;
