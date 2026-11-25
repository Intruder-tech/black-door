package io.github.intruder_tech.blackdoor.violations;

/** Evidence class of a finding (docs/DETECTION_REGISTRY.md section 1). */
public enum Confidence {
    /** Impossible under the protocol or physics: no legitimate client can produce it. */
    DETERMINISTIC,
    /** Prediction-based with small residual uncertainty. */
    HIGH,
    /** Pattern-based. Never punishes on its own. */
    HEURISTIC,
    /** Probabilistic. Review only. */
    STATISTICAL
}
