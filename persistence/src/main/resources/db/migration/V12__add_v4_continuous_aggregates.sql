-- Daily market event summary aggregate

CREATE MATERIALIZED VIEW daily_event_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    time_bucket('1 day', time) AS day,
    event_type,
    COUNT(*) AS event_count,
    AVG(magnitude) AS avg_magnitude,
    MAX(magnitude) AS max_magnitude
FROM market_events
GROUP BY day, event_type;
