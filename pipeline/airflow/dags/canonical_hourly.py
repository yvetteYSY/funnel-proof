"""Hourly, partition-idempotent canonical rebuild for the FunnelProof Gold funnel."""

from __future__ import annotations

import subprocess
from datetime import datetime, timedelta
from pathlib import Path

try:  # Airflow 3 first, with a compatibility fallback for local Airflow 2 users.
    from airflow.sdk import dag, get_current_context, task
except ImportError:  # pragma: no cover - resolved by the installed Airflow version
    from airflow.decorators import dag, task
    from airflow.operators.python import get_current_context

from canonical_rebuild_config import default_clickhouse_publisher_config, default_config

PROJECT_ROOT = Path(__file__).resolve().parents[3]


@dag(
    dag_id="funnelproof_canonical_hourly",
    description="Rebuild canonical Silver and Gold funnel partitions from immutable Bronze",
    schedule="15 * * * *",
    start_date=datetime(2026, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args={"retries": 2, "retry_delay": timedelta(minutes=5)},
    tags=["funnelproof", "canonical", "iceberg"],
)
def canonical_hourly():
    @task
    def rebuild_current_event_date() -> str:
        context = get_current_context()
        interval_end = context["data_interval_end"]
        event_date = interval_end.date()
        snapshot_version = f"airflow-{context['run_id']}"
        command = default_config(PROJECT_ROOT).command(event_date, event_date, snapshot_version)
        subprocess.run(command, check=True)
        return snapshot_version

    @task
    def publish_serving_snapshot(snapshot_version: str) -> None:
        event_date = get_current_context()["data_interval_end"].date()
        subprocess.run(default_clickhouse_publisher_config(PROJECT_ROOT).command(event_date, snapshot_version), check=True)

    publish_serving_snapshot(rebuild_current_event_date())


canonical_hourly()
