from datetime import date
import unittest

from dags.canonical_rebuild_config import CanonicalRebuildConfig, ClickHousePublisherConfig


class CanonicalRebuildConfigTest(unittest.TestCase):
    def test_constructs_an_explicit_idempotent_partition_command(self) -> None:
        config = CanonicalRebuildConfig(
            spark_submit="spark-submit",
            job_jar="/jobs/canonical.jar",
            bronze_table="lake.funnelproof.bronze_events_v1",
            silver_table="lake.funnelproof.silver_events_v1",
            gold_table="lake.funnelproof.gold_funnel_daily_v1",
        )

        command = config.command(date(2026, 8, 18), date(2026, 8, 18), "airflow-manual-run")

        self.assertIn("--start-date", command)
        self.assertIn("2026-08-18", command)
        self.assertIn("--snapshot-version", command)
        self.assertEqual(command[-1], "airflow-manual-run")

    def test_rejects_an_invalid_range(self) -> None:
        config = CanonicalRebuildConfig("spark-submit", "/jobs/canonical.jar", "bronze", "silver", "gold")
        with self.assertRaises(ValueError):
            config.command(date(2026, 8, 19), date(2026, 8, 18), "invalid")

    def test_publisher_passes_only_password_environment_variable(self) -> None:
        command = ClickHousePublisherConfig("spark-submit", "/job.jar", "gold", "jdbc:clickhouse://localhost", "default").command(date(2026, 8, 18), "run")
        self.assertIn("--clickhouse-password-env", command)
        self.assertIn("FUNNEL_PROOF_CLICKHOUSE_PASSWORD", command)


if __name__ == "__main__":
    unittest.main()
