package dev.funnelproof.pipeline.spark

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

final case class CanonicalEvent(
  workspaceId: String,
  eventId: String,
  anonymousId: String,
  eventName: String,
  occurredAt: Instant,
  funnelDefinitionVersion: String
)

final case class DailyFunnelCount(
  workspaceId: String,
  eventDate: LocalDate,
  funnelDefinitionVersion: String,
  stageName: String,
  uniqueUsers: Long
)

/** Pure canonical funnel logic. Spark I/O is kept in CanonicalRebuildJob. */
object FunnelRules {
  val Stages: Vector[String] = Vector("vpv", "signup_completed", "activation_completed", "subscription_started")

  def deduplicate(events: Seq[CanonicalEvent]): Seq[CanonicalEvent] =
    events.groupBy(event => (event.workspaceId, event.eventId)).values.map(_.minBy(_.occurredAt)).toSeq

  def dailyCounts(events: Seq[CanonicalEvent]): Seq[DailyFunnelCount] = {
    val qualifying = deduplicate(events)
      .filter(event => Stages.contains(event.eventName))
      .groupBy(event => (event.workspaceId, event.anonymousId, event.funnelDefinitionVersion))
      .values
      .flatMap(qualifyingStages)

    qualifying
      .groupBy(event => (event.workspaceId, event.occurredAt.atZone(ZoneOffset.UTC).toLocalDate,
        event.funnelDefinitionVersion, event.eventName))
      .map { case ((workspaceId, eventDate, definitionVersion, stageName), stageEvents) =>
        DailyFunnelCount(workspaceId, eventDate, definitionVersion, stageName, stageEvents.map(_.anonymousId).toSeq.distinct.size.toLong)
      }
      .toSeq
      .sortBy(count => (count.workspaceId, count.eventDate.toString, Stages.indexOf(count.stageName)))
  }

  private def qualifyingStages(events: Seq[CanonicalEvent]): Seq[CanonicalEvent] = {
    val firstByStage = events.groupBy(_.eventName).view.mapValues(_.minBy(_.occurredAt)).toMap
    Stages.foldLeft(Vector.empty[CanonicalEvent]) { (accepted, stage) =>
      firstByStage.get(stage) match {
        case Some(event) if accepted.lastOption.forall(_.occurredAt.compareTo(event.occurredAt) <= 0) => accepted :+ event
        case _ => accepted
      }
    }
  }
}
