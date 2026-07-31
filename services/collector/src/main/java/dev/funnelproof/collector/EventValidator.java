package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Enforces the V1 privacy-first event contract before an event can reach a store.
 * It returns reason codes only; callers must not log rejected payloads.
 */
public final class EventValidator {
    private static final Set<String> EVENT_NAMES = Set.of(
            "vpv", "click", "signup_completed", "activation_completed", "subscription_started"
    );
    private static final Set<String> ALLOWED_INPUT_FIELDS = Set.of(
            "event_id", "event_name", "occurred_at", "anonymous_id", "session_id", "user_id",
            "properties", "context", "schema_version", "tracking_plan_version", "funnel_definition_version", "consent"
    );
    private static final Set<String> CONTEXT_FIELDS = Set.of("platform", "sdk_version");
    private static final Set<String> CONSENT_FIELDS = Set.of("analytics");
    private static final Map<String, Set<String>> ALLOWED_PROPERTIES = Map.of(
            "vpv", Set.of("page_path"),
            "click", Set.of("ui_name", "ui_type", "ui_surface"),
            "signup_completed", Set.of("signup_method"),
            "activation_completed", Set.of("activation_action"),
            "subscription_started", Set.of("plan", "billing_interval", "acquisition_channel")
    );
    private static final Map<String, Set<String>> REQUIRED_PROPERTIES = Map.of(
            "vpv", Set.of("page_path"),
            "click", Set.of("ui_name"),
            "signup_completed", Set.of(),
            "activation_completed", Set.of("activation_action"),
            "subscription_started", Set.of("plan", "billing_interval")
    );
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "email|password|token|secret|card|address|content|query|referrer|title|html|text",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_PAST_EVENT_AGE = Duration.ofDays(365);

    public ValidationResult validate(JsonNode candidate, Instant receivedAt) {
        if (!(candidate instanceof ObjectNode input)) return ValidationResult.rejected("invalid_payload");

        ValidationResult envelopeResult = validateEnvelope(input);
        if (!envelopeResult.accepted()) return envelopeResult;
        String eventName = text(input, "event_name");
        if (!EVENT_NAMES.contains(eventName)) return ValidationResult.rejected("unsupported_event_name");
        if (!hasPseudonymousId(input, "event_id") || !hasPseudonymousId(input, "anonymous_id") || !hasPseudonymousId(input, "session_id")) {
            return ValidationResult.rejected("invalid_identifier");
        }
        if (input.has("user_id") && !hasPseudonymousId(input, "user_id")) return ValidationResult.rejected("invalid_identifier");
        if (!hasVersion(input, "schema_version") || !hasVersion(input, "tracking_plan_version") || !hasVersion(input, "funnel_definition_version")) {
            return ValidationResult.rejected("unsupported_contract_version");
        }
        if (!hasValidConsent(input) || !hasValidContext(input)) return ValidationResult.rejected("invalid_context_or_consent");

        ValidationResult timestampResult = validateEventTime(input, receivedAt);
        if (!timestampResult.accepted()) return timestampResult;

        JsonNode propertiesNode = input.get("properties");
        if (!(propertiesNode instanceof ObjectNode properties)) return ValidationResult.rejected("invalid_properties");
        ValidationResult propertiesResult = validateProperties(eventName, properties);
        if (!propertiesResult.accepted()) return propertiesResult;

        ObjectNode accepted = input.deepCopy();
        accepted.put("received_at", receivedAt.toString());
        accepted.put("event_time_quality", "client_verified");
        return ValidationResult.accepted(accepted);
    }

    private ValidationResult validateEnvelope(ObjectNode input) {
        var fields = input.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED_INPUT_FIELDS.contains(fields.next())) return ValidationResult.rejected("unallowed_envelope_field");
        }
        return ValidationResult.accepted(null);
    }

    private ValidationResult validateEventTime(ObjectNode input, Instant receivedAt) {
        String occurredAt = text(input, "occurred_at");
        if (occurredAt == null) return ValidationResult.rejected("missing_occurred_at");

        try {
            Instant occurred = Instant.parse(occurredAt);
            if (occurred.isAfter(receivedAt.plus(MAX_FUTURE_CLOCK_SKEW)) || occurred.isBefore(receivedAt.minus(MAX_PAST_EVENT_AGE))) {
                return ValidationResult.rejected("invalid_event_time");
            }
        } catch (DateTimeParseException exception) {
            return ValidationResult.rejected("invalid_event_time");
        }
        return ValidationResult.accepted(null);
    }

    private ValidationResult validateProperties(String eventName, ObjectNode properties) {
        var fields = properties.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();
            if (SENSITIVE_KEY.matcher(key).find()) return ValidationResult.rejected("sensitive_property");
            if (!ALLOWED_PROPERTIES.get(eventName).contains(key)) return ValidationResult.rejected("unallowed_property");
            if (!properties.get(key).isTextual()) return ValidationResult.rejected("invalid_property_type");
        }
        for (String required : REQUIRED_PROPERTIES.get(eventName)) {
            if (!properties.hasNonNull(required)) return ValidationResult.rejected("missing_required_property");
        }

        return switch (eventName) {
            case "vpv" -> isValidPagePath(text(properties, "page_path"))
                    ? ValidationResult.accepted(null) : ValidationResult.rejected("invalid_page_path");
            case "click" -> isIdentifier(text(properties, "ui_name"))
                    && optionalIdentifier(properties, "ui_surface")
                    && optionalEnum(properties, "ui_type", Set.of("button", "link", "menu_item"))
                    ? ValidationResult.accepted(null) : ValidationResult.rejected("invalid_ui_component");
            case "activation_completed" -> isIdentifier(text(properties, "activation_action"))
                    ? ValidationResult.accepted(null) : ValidationResult.rejected("invalid_activation_action");
            case "subscription_started" -> isIdentifier(text(properties, "plan"))
                    && optionalIdentifier(properties, "acquisition_channel")
                    && Set.of("monthly", "yearly").contains(text(properties, "billing_interval"))
                    ? ValidationResult.accepted(null) : ValidationResult.rejected("invalid_subscription_properties");
            case "signup_completed" -> optionalEnum(properties, "signup_method", Set.of("email", "google", "github", "sso"))
                    ? ValidationResult.accepted(null) : ValidationResult.rejected("invalid_signup_method");
            default -> ValidationResult.rejected("unsupported_event_name");
        };
    }

    private static boolean hasPseudonymousId(ObjectNode node, String field) {
        String value = text(node, field);
        return value != null && value.length() >= 16 && value.length() <= 128;
    }

    private static boolean hasVersion(ObjectNode node, String field) {
        return "1.0.0".equals(text(node, field));
    }

    private static boolean hasValidConsent(ObjectNode node) {
        JsonNode consent = node.get("consent");
        if (consent == null || !consent.isObject() || !consent.path("analytics").isBoolean() || !consent.path("analytics").booleanValue()) {
            return false;
        }
        var fields = consent.fieldNames();
        while (fields.hasNext()) {
            if (!CONSENT_FIELDS.contains(fields.next())) return false;
        }
        return true;
    }

    private static boolean hasValidContext(ObjectNode node) {
        JsonNode context = node.get("context");
        if (context == null || !context.isObject() || !"web".equals(context.path("platform").asText()) || context.path("sdk_version").asText().isBlank()) {
            return false;
        }
        var fields = context.fieldNames();
        while (fields.hasNext()) {
            if (!CONTEXT_FIELDS.contains(fields.next())) return false;
        }
        return true;
    }

    private static boolean isValidPagePath(String pagePath) {
        return pagePath != null && pagePath.startsWith("/") && !pagePath.contains("?") && !pagePath.contains("#") && pagePath.length() <= 512;
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static boolean optionalIdentifier(ObjectNode properties, String field) {
        return !properties.has(field) || isIdentifier(text(properties, field));
    }

    private static boolean optionalEnum(ObjectNode properties, String field, Set<String> acceptedValues) {
        return !properties.has(field) || acceptedValues.contains(text(properties, field));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
