CREATE TABLE IF NOT EXISTS funnelproof.gold_funnel_daily_v1 (
    workspace_id STRING NOT NULL,
    event_date DATE NOT NULL,
    funnel_definition_version STRING NOT NULL,
    stage_name STRING NOT NULL,
    unique_users BIGINT NOT NULL,
    snapshot_version STRING NOT NULL,
    canonical_completed_at TIMESTAMP_LTZ(3) NOT NULL,
    data_sla_status STRING NOT NULL
)
USING iceberg
PARTITIONED BY (event_date, bucket(16, workspace_id))
TBLPROPERTIES ('format-version' = '2', 'write.format.default' = 'parquet');
