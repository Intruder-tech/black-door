package io.github.intruder_tech.blackdoor.core.antixray;

/**
 * How strong Paper's anti-xray is. Normal is engine-mode 1, Strong is engine-mode 2 (an existing
 * engine-mode 3 counts as Strong too).
 */
public enum AntiXrayStrength {
    OFF("Off"), NORMAL("Normal"), STRONG("Strong");

    private final String label;

    AntiXrayStrength(String label) {
        this.label = label;
    }

    /** The word shown to operators. */
    public String label() {
        return label;
    }

    /** The next choice in the control panel: Off, Normal, Strong, Off. */
    public AntiXrayStrength next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
