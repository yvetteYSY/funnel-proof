"""Pure command construction for the canonical-rebuild DAG; deliberately independent of Airflow."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from pathlib import Path


@dataclass(frozen=True)
class CanonicalRebuildConfig:
    spark_submit: str
    job_jar: str
    bronze_table: str
    silver_table: str
    gold_table: str
    lookback_days: int = 30
    iceberg_package: str = "org.apache.iceberg:iceberg-spark-runtime-4.1_2.13:1.11.0"

    def command(self, start_date: date, end_date: date, snapshot_version: str) -> list[str]:
        if end_date < start_date:
            raise ValueError("end_date must be on or after start_date")
        if self.lookback_days < 1:
            raise ValueError("lookback_days must be positive")
        return [
            self.spark_submit,
            "--class", "dev.funnelproof.pipeline.spark.CanonicalRebuildJob",
            "--packages", self.iceberg_package,
            self.job_jar,
            "--bronze-table", self.bronze_table,
            "--silver-table", self.silver_table,
            "--gold-table", self.gold_table,
            "--start-date", start_date.isoformat(),
            "--end-date", end_date.isoformat(),
            "--lookback-days", str(self.lookback_days),
            "--snapshot-version", snapshot_version,
        ]


def default_config(project_root: Path) -> CanonicalRebuildConfig:
    import os

    return CanonicalRebuildConfig(
        spark_submit=os.environ.get("FUNNEL_PROOF_SPARK_SUBMIT", "spark-submit"),
        job_jar=os.environ.get(
            "FUNNEL_PROOF_SPARK_JOB_JAR",
            str(project_root / "pipeline/spark/target/spark-canonical-rebuild-0.1.0-SNAPSHOT.jar"),
        ),
        bronze_table=os.environ.get("FUNNEL_PROOF_ICEBERG_BRONZE_TABLE", "lake.funnelproof.bronze_events_v1"),
        silver_table=os.environ.get("FUNNEL_PROOF_ICEBERG_SILVER_TABLE", "lake.funnelproof.silver_events_v1"),
        gold_table=os.environ.get("FUNNEL_PROOF_ICEBERG_GOLD_TABLE", "lake.funnelproof.gold_funnel_daily_v1"),
        lookback_days=int(os.environ.get("FUNNEL_PROOF_FUNNEL_LOOKBACK_DAYS", "30")),
    )
