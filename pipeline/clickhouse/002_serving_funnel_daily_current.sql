CREATE VIEW IF NOT EXISTS funnelproof.funnel_daily_current_v1 AS
SELECT
    workspace_id,
    event_date,
    funnel_definition_version,
    stage_name,
    argMax(unique_users, snapshot_version) AS unique_users,
    max(snapshot_version) AS snapshot_version,
    argMax(canonical_completed_at, snapshot_version) AS canonical_completed_at,
    argMax(data_sla_status, snapshot_version) AS data_sla_status
FROM funnelproof.funnel_daily_v1
GROUP BY workspace_id, event_date, funnel_definition_version, stage_name;
