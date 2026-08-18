package dev.funnelproof.pipeline.spark

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

import java.time.Instant

class FunnelRulesTest {
  private def event(id: String, name: String, timestamp: String): CanonicalEvent =
    CanonicalEvent("demo_workspace", id, "anonymous-1", name, Instant.parse(timestamp), "1.0.0")

  @Test def deduplicatesByWorkspaceAndStableEventId(): Unit = {
    val first = event("one", "vpv", "2026-08-18T09:00:00Z")
    val replay = event("one", "vpv", "2026-08-18T09:02:00Z")
    assertEquals(Seq(first), FunnelRules.deduplicate(Seq(replay, first)))
  }

  @Test def onlyCountsStagesReachedInSequence(): Unit = {
    val results = FunnelRules.dailyCounts(Seq(
      event("one", "vpv", "2026-08-18T09:00:00Z"),
      event("two", "signup_completed", "2026-08-18T09:01:00Z"),
      event("three", "activation_completed", "2026-08-18T09:02:00Z"),
      event("four", "subscription_started", "2026-08-18T09:03:00Z"),
      event("five", "subscription_started", "2026-08-18T10:00:00Z")
    ))
    assertEquals(Seq("vpv", "signup_completed", "activation_completed", "subscription_started"), results.map(_.stageName))
    assertEquals(Seq(1L, 1L, 1L, 1L), results.map(_.uniqueUsers))
  }
}
