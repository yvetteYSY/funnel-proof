package dev.funnelproof.pipeline.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, countDistinct, lit, min, row_number, to_date, when}

import java.time.{Instant, LocalDate}
import scala.util.Try

/**
 * Rebuilds a requested event-date range from immutable Iceberg Bronze.
 * The input range is explicit, so retries/backfills replace deterministic partitions rather than append counts.
 */
object CanonicalRebuildJob {
  final case class Settings(
    bronzeTable: String,
    silverTable: String,
    goldTable: String,
    startDate: LocalDate,
    endDate: LocalDate,
    lookbackDays: Int,
    snapshotVersion: String,
    completedAt: Instant
  )

  def main(args: Array[String]): Unit = {
    val settings = Settings.from(args)
    val spark = SparkSession.builder()
      .appName("funnelproof-canonical-rebuild")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .config("spark.sql.adaptive.skewJoin.enabled", "true")
      .getOrCreate()

    try rebuild(spark, settings)
    finally spark.stop()
  }

  def rebuild(spark: SparkSession, settings: Settings): Unit = {
    import spark.implicits._
    val start = java.sql.Timestamp.valueOf(settings.startDate.atStartOfDay())
    val endExclusive = java.sql.Timestamp.valueOf(settings.endDate.plusDays(1).atStartOfDay())
    val datePredicate = col("occurred_at").geq(lit(start)).and(col("occurred_at").lt(lit(endExclusive)))

    val silver = spark.table(settings.bronzeTable)
      .filter(datePredicate)
      .withColumn("dedupe_rank", row_number().over(Window
        .partitionBy("workspace_id", "event_id")
        .orderBy(col("received_at").asc, col("occurred_at").asc)))
      .filter(col("dedupe_rank") === 1)
      .drop("dedupe_rank")
      .select(
        col("workspace_id"), col("event_id"), col("anonymous_id"), col("event_name"),
        col("occurred_at"), col("received_at"), col("schema_version"),
        col("tracking_plan_version"), col("funnel_definition_version"), col("accepted_event_json"))

    overwriteDateRange(silver, settings.silverTable, settings.startDate, settings.endDate, "occurred_at")

    val historyStart = java.sql.Timestamp.valueOf(settings.startDate.minusDays(settings.lookbackDays).atStartOfDay())
    val gold = canonicalGold(spark.table(settings.silverTable)
      .filter(col("occurred_at").geq(lit(historyStart)).and(col("occurred_at").lt(lit(endExclusive)))), settings, start, endExclusive)
      .withColumn("snapshot_version", lit(settings.snapshotVersion))
      .withColumn("canonical_completed_at", lit(java.sql.Timestamp.from(settings.completedAt)))
      .withColumn("data_sla_status", lit("healthy"))

    overwriteDateRange(gold, settings.goldTable, settings.startDate, settings.endDate, "event_date")
  }

  private def canonicalGold(events: DataFrame, settings: Settings, start: java.sql.Timestamp, endExclusive: java.sql.Timestamp): DataFrame = {
    val stages = FunnelRules.Stages
    val stageMinimums = stages.map(stage => min(when(col("event_name") === stage, col("occurred_at"))).as(s"${stage}_at"))
    val firstStageTimes = events
      .filter(col("event_name").isin(stages: _*))
      .groupBy("workspace_id", "anonymous_id", "funnel_definition_version")
      .agg(stageMinimums.head, stageMinimums.tail: _*)

    val qualified = Seq(
      ("vpv", col("vpv_at").isNotNull),
      ("signup_completed", col("vpv_at").isNotNull && col("signup_completed_at").geq(col("vpv_at"))),
      ("activation_completed", col("signup_completed_at").isNotNull && col("activation_completed_at").geq(col("signup_completed_at"))),
      ("subscription_started", col("activation_completed_at").isNotNull && col("subscription_started_at").geq(col("activation_completed_at")))
    ).map { case (stage, qualifies) =>
      firstStageTimes.filter(qualifies)
        .select(col("workspace_id"), col("anonymous_id"), col("funnel_definition_version"),
          lit(stage).as("stage_name"), col(s"${stage}_at").as("stage_occurred_at"))
    }.reduce(_.unionByName(_))

    qualified
      .filter(col("stage_occurred_at").geq(lit(start)).and(col("stage_occurred_at").lt(lit(endExclusive))))
      .withColumn("event_date", to_date(col("stage_occurred_at")))
      .groupBy("workspace_id", "event_date", "funnel_definition_version", "stage_name")
      .agg(countDistinct("anonymous_id").as("unique_users"))
  }

  private def overwriteDateRange(frame: DataFrame, table: String, start: LocalDate, end: LocalDate, dateColumn: String): Unit = {
    val predicate = s"$dateColumn >= DATE '${start}' AND $dateColumn <= DATE '${end}'"
    frame.writeTo(table).overwrite(org.apache.spark.sql.functions.expr(predicate))
  }

  object Settings {
    def from(args: Array[String]): Settings = {
      val values = args.grouped(2).collect { case Array(key, value) if key.startsWith("--") => key.drop(2) -> value }.toMap
      def required(name: String): String = values.getOrElse(name, throw new IllegalArgumentException(s"--$name is required"))
      def date(name: String): LocalDate = Try(LocalDate.parse(required(name))).getOrElse(throw new IllegalArgumentException(s"--$name must be YYYY-MM-DD"))
      val start = date("start-date")
      val end = date("end-date")
      require(!end.isBefore(start), "--end-date must be on or after --start-date")
      val lookbackDays = values.get("lookback-days").map(_.toInt).getOrElse(30)
      require(lookbackDays >= 1, "--lookback-days must be positive")
      Settings(required("bronze-table"), required("silver-table"), required("gold-table"), start, end, lookbackDays,
        values.getOrElse("snapshot-version", s"canonical-${Instant.now().toEpochMilli}"), Instant.now())
    }
  }
}
