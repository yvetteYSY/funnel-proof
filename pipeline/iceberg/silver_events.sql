CREATE TABLE IF NOT EXISTS funnelproof.silver_events_v1 (
    workspace_id STRING NOT NULL,
    event_id STRING NOT NULL,
    anonymous_id STRING NOT NULL,
    event_name STRING NOT NULL,
    occurred_at TIMESTAMP_LTZ(3) NOT NULL,
    received_at TIMESTAMP_LTZ(3) NOT NULL,
    schema_version STRING NOT NULL,
    tracking_plan_version STRING NOT NULL,
    funnel_definition_version STRING NOT NULL,
    accepted_event_json STRING NOT NULL
)
USING iceberg
PARTITIONED BY (days(occurred_at), bucket(16, workspace_id))
TBLPROPERTIES ('format-version' = '2', 'write.format.default' = 'parquet');
