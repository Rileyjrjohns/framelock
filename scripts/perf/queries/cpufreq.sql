-- Time-weighted mean frequency of each CPU over the trace, in MHz: how hard the system pushed each core.
WITH f AS (
  SELECT t.cpu, c.ts, c.value,
    LEAD(c.ts) OVER (PARTITION BY t.cpu ORDER BY c.ts) - c.ts AS dur
  FROM counter c
  JOIN cpu_counter_track t ON c.track_id = t.id
  WHERE t.name = 'cpufreq'
)
SELECT cpu, ROUND(SUM(value * dur) / SUM(dur) / 1000) AS mean_mhz, ROUND(MAX(value) / 1000) AS max_mhz
FROM f WHERE dur IS NOT NULL
GROUP BY cpu ORDER BY cpu;
