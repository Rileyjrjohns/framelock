-- CPU time of the app's busiest threads over the whole trace, in seconds, and the share spent on each CPU.
-- On many phones the last CPUs are the big cores: compare where 'inference' runs with the GPU and the CPU backends.
SELECT t.name AS thread, s.cpu,
  ROUND(SUM(s.dur) / 1e9, 2) AS cpu_s
FROM sched s
JOIN thread t USING (utid)
JOIN process p USING (upid)
WHERE p.name LIKE 'dev.framelock%'
GROUP BY t.name, s.cpu
HAVING cpu_s > 1
ORDER BY thread, s.cpu;
