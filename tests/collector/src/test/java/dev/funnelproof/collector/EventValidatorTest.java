package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant RECEIVED_AT = Instant.parse("2026-07-30T18:45:00.240Z");
    private final EventValidator validator = new EventValidator();

    @Test
    void acceptsACompleteSubscriptionEventAndAddsServerReceiptTime() throws Exception {
        ValidationResult result = validator.validate(MAPPER.readTree("""
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f20",
                  "event_name":"subscription_started",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f21",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f22",
                  "properties":{"plan":"pro","billing_interval":"monthly"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """), RECEIVED_AT);

        assertTrue(result.accepted());
        assertEquals("2026-07-30T18:45:00.240Z", result.event().path("received_at").asText());
        assertEquals("client_verified", result.event().path("event_time_quality").asText());
    }

    @Test
    void rejectsSensitivePropertiesBeforePersistence() throws Exception {
        ValidationResult result = validator.validate(MAPPER.readTree("""
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f30",
                  "event_name":"activation_completed",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f31",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f32",
                  "properties":{"activation_action":"first_project_created","email":"person@example.com"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """), RECEIVED_AT);

        assertFalse(result.accepted());
        assertEquals("sensitive_property", result.reasonCode());
    }

    @Test
    void rejectsVpvRoutesThatContainQueryParameters() throws Exception {
        ValidationResult result = validator.validate(MAPPER.readTree("""
                {
                  "event_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f40",
                  "event_name":"vpv",
                  "occurred_at":"2026-07-30T18:45:00.123Z",
                  "anonymous_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f41",
                  "session_id":"018f4f70-8ab1-7cc4-a67d-fae57c0c0f42",
                  "properties":{"page_path":"/pricing?email=person@example.com"},
                  "context":{"platform":"web","sdk_version":"0.1.0"},
                  "schema_version":"1.0.0",
                  "tracking_plan_version":"1.0.0",
                  "funnel_definition_version":"1.0.0",
                  "consent":{"analytics":true}
                }
                """), RECEIVED_AT);

        assertFalse(result.accepted());
        assertEquals("invalid_page_path", result.reasonCode());
    }
}
