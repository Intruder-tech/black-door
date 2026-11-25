package io.github.intruder_tech.blackdoor.violations;

import java.util.Objects;

/**
 * One observation by a check. Checks report findings; they never act on players themselves
 * (docs/ARCHITECTURE.md section 4.1).
 *
 * @param checkId    stable check identifier, e.g. {@code packet.invalid-rotation}
 * @param detail     short, already-bounded description of the evidence (no secrets)
 * @param mitigated  whether the triggering packet/action was blocked
 */
public record Finding(String checkId, Subject subject, Confidence confidence, String detail,
                      long timeMillis, boolean mitigated) {

    public Finding {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(detail, "detail");
    }
}
