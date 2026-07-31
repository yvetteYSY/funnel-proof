package dev.funnelproof.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record ValidationResult(boolean accepted, String reasonCode, ObjectNode event) {
    public static ValidationResult accepted(ObjectNode event) {
        return new ValidationResult(true, null, event);
    }

    public static ValidationResult rejected(String reasonCode) {
        return new ValidationResult(false, reasonCode, null);
    }
}
