CREATE DATABASE IF NOT EXISTS funnelproof;

CREATE TABLE IF NOT EXISTS funnelproof.funnel_daily_v1
(
    workspace_id LowCardinality(String),
    event_date Date,
    funnel_definition_version LowCardinality(String),
    stage_name LowCardinality(String),
    unique_users UInt64,
    snapshot_version String,
    canonical_completed_at DateTime64(3, 'UTC'),
    data_sla_status LowCardinality(String)
)
ENGINE = ReplacingMergeTree(snapshot_version)
PARTITION BY toYYYYMM(event_date)
ORDER BY (workspace_id, event_date, funnel_definition_version, stage_name)
SETTINGS index_granularity = 8192;

-- Serving queries must include workspace_id. This table intentionally contains aggregates only;
-- it is not a substitute for tenant authorization in the API layer.
