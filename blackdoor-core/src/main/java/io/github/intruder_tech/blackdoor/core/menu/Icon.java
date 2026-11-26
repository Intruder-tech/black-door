package io.github.intruder_tech.blackdoor.core.menu;

/** Platform-neutral icons; each platform maps them to items that exist on every supported version. */
public enum Icon {
    MODE_MONITOR, MODE_ENFORCE, CHECK_DISABLED,
    CATEGORY, LOCKED_COMBAT, LOCKED_XRAY, STATUS, INFO, RELOAD, BACK, CLOSE, SETUP, JOIN_GUARD,
    // one per check, so owners can tell them apart at a glance
    POSITION, ROTATION, SLOT, SELF_INTERACT, TELEPORT, OVERSIZED, TRANSACTION, FLOOD,
    PAYLOAD, BOOK, SIGN, CREATIVE_ITEM
}
