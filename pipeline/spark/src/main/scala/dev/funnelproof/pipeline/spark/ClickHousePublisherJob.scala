package dev.funnelproof.pipeline.spark

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col

import java.sql.{Date, DriverManager, PreparedStatement, Timestamp}
import java.time.LocalDate
import scala.util.Try

/** Publishes one canonical Gold snapshot to ClickHouse; only aggregate rows leave Iceberg. */
object ClickHousePublisherJob {
  def main(args: Array[String]): Unit = {
    val settings = Settings.from(args)
    val password = Option(System.getenv(settings.passwordEnvironmentVariable)).filter(_.nonEmpty)
      .getOrElse(throw new IllegalArgumentException(s"${settings.passwordEnvironmentVariable} is required"))
    val spark = SparkSession.builder().appName("funnelproof-clickhouse-publisher").getOrCreate()
    try spark.table(settings.goldTable)
      .filter(col("event_date") === Date.valueOf(settings.eventDate))
      .filter(col("snapshot_version") === settings.snapshotVersion)
      .select("workspace_id", "event_date", "funnel_definition_version", "stage_name", "unique_users", "snapshot_version", "canonical_completed_at", "data_sla_status")
      .foreachPartition(rows => writePartition(rows, settings, password))
    finally spark.stop()
  }

  private def writePartition(rows: Iterator[org.apache.spark.sql.Row], settings: Settings, password: String): Unit = {
    Class.forName("com.clickhouse.jdbc.ClickHouseDriver")
    val connection = DriverManager.getConnection(settings.jdbcUrl, settings.user, password)
    val statement = connection.prepareStatement("INSERT INTO funnelproof.funnel_daily_v1 (workspace_id, event_date, funnel_definition_version, stage_name, unique_users, snapshot_version, canonical_completed_at, data_sla_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    try rows.grouped(500).foreach { batch => batch.foreach { row => bind(statement, row); statement.addBatch() }; statement.executeBatch() }
    finally { statement.close(); connection.close() }
  }

  private def bind(statement: PreparedStatement, row: org.apache.spark.sql.Row): Unit = {
    statement.setString(1, row.getAs[String]("workspace_id")); statement.setDate(2, row.getAs[Date]("event_date"))
    statement.setString(3, row.getAs[String]("funnel_definition_version")); statement.setString(4, row.getAs[String]("stage_name"))
    statement.setLong(5, row.getAs[Long]("unique_users")); statement.setString(6, row.getAs[String]("snapshot_version"))
    statement.setTimestamp(7, row.getAs[Timestamp]("canonical_completed_at")); statement.setString(8, row.getAs[String]("data_sla_status"))
  }

  final case class Settings(goldTable: String, jdbcUrl: String, user: String, passwordEnvironmentVariable: String, eventDate: LocalDate, snapshotVersion: String)
  object Settings {
    def from(args: Array[String]): Settings = {
      val values = args.grouped(2).collect { case Array(key, value) if key.startsWith("--") => key.drop(2) -> value }.toMap
      def required(name: String): String = values.getOrElse(name, throw new IllegalArgumentException(s"--$name is required"))
      val eventDate = Try(LocalDate.parse(required("event-date"))).getOrElse(throw new IllegalArgumentException("--event-date must be YYYY-MM-DD"))
      Settings(required("gold-table"), required("clickhouse-jdbc-url"), required("clickhouse-user"), values.getOrElse("clickhouse-password-env", "FUNNEL_PROOF_CLICKHOUSE_PASSWORD"), eventDate, required("snapshot-version"))
    }
  }
}
