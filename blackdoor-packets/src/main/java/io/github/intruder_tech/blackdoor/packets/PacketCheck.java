package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.violations.Confidence;

import java.util.Locale;

/** Packet-level checks (docs/DETECTION_REGISTRY.md section 6). */
public enum PacketCheck {
    INVALID_POSITION(Confidence.DETERMINISTIC, "Non-finite or out-of-world coordinates in a movement packet"),
    INVALID_ROTATION(Confidence.DETERMINISTIC, "Pitch outside -90..90 or non-finite rotation"),
    INVALID_SLOT(Confidence.DETERMINISTIC, "Hotbar slot outside 0..8"),
    SELF_INTERACT(Confidence.DETERMINISTIC, "Attacking or interacting with your own entity"),
    TELEPORT_CONFIRM(Confidence.DETERMINISTIC, "Confirming a teleport the server never sent"),
    OVERSIZED(Confidence.DETERMINISTIC, "Packet or text larger than any legitimate client sends"),
    TRANSACTION(Confidence.DETERMINISTIC, "Reply to a connection check that was never sent, or replies out of order"),
    FLOOD(Confidence.HEURISTIC, "Packet rate above configured limits"),
    PAYLOAD(Confidence.HEURISTIC, "Plugin message or client brand larger than configured limits"),
    BOOK(Confidence.HEURISTIC, "Book with more pages or longer text than configured limits"),
    SIGN(Confidence.DETERMINISTIC, "Sign text with too many or too long lines"),
    CREATIVE_ITEM(Confidence.HEURISTIC, "Creative inventory action with an impossible slot, amount or size"),
    ITEM_DATA(Confidence.HEURISTIC, "Item data nested deeper or holding more entries than configured limits"),
    GAME_MODE(Confidence.DETERMINISTIC, "Creative inventory action while the player is not in creative mode");

    private final Confidence confidence;
    private final String description;

    PacketCheck(Confidence confidence, String description) {
        this.confidence = confidence;
        this.description = description;
    }

    /** Stable id, e.g. {@code packet.invalid-rotation}. */
    public String id() {
        return "packet." + configKey();
    }

    /** Key in checks.yml, e.g. {@code invalid-rotation}. */
    public String configKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public Confidence confidence() {
        return confidence;
    }

    public String description() {
        return description;
    }
}
