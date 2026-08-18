# Airflow orchestration

`funnelproof_canonical_hourly` is an hourly Airflow DAG that launches the Spark canonical rebuild at 15 minutes past the hour. It rebuilds the event-date partition containing the completed interval; the Spark job's explicit overwrite makes retries idempotent.

## Operational behavior

- `max_active_runs=1` prevents two scheduler runs from replacing the same date partition concurrently.
- Two bounded retries handle transient submission failures. A failed task does not publish a partial Gold snapshot because the Spark job writes only after a completed transformation.
- `snapshot_version` contains the Airflow run ID, allowing downstream ClickHouse publication and the dashboard to identify the exact canonical result.
- Historical replay is performed with a manual DAG run and an explicit date range through the same Spark job. It is not a Kafka offset reset.

The DAG uses only environment-driven runtime configuration:

```bash
export FUNNEL_PROOF_SPARK_SUBMIT=/opt/spark/bin/spark-submit
export FUNNEL_PROOF_SPARK_JOB_JAR=/opt/funnelproof/spark-canonical-rebuild.jar
export FUNNEL_PROOF_ICEBERG_BRONZE_TABLE=lake.funnelproof.bronze_events_v1
export FUNNEL_PROOF_ICEBERG_SILVER_TABLE=lake.funnelproof.silver_events_v1
export FUNNEL_PROOF_ICEBERG_GOLD_TABLE=lake.funnelproof.gold_funnel_daily_v1
export FUNNEL_PROOF_FUNNEL_LOOKBACK_DAYS=30
```

Install the DAG directory in Airflow's DAGs folder and build the Spark jar before enabling it. Credentials belong in the Airflow/Spark deployment secret store, never in the DAG or a committed `.env` file.

Run the pure DAG-command tests without installing Airflow:

```bash
make airflow-test
```
