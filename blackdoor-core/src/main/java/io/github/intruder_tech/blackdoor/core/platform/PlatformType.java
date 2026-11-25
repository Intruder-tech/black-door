package io.github.intruder_tech.blackdoor.core.platform;

/** Server or proxy software Black Door is running on. */
public enum PlatformType {
    BUKKIT(false),
    SPIGOT(false),
    PAPER(false),
    PURPUR(false),
    FOLIA(false),
    VELOCITY(true),
    BUNGEECORD(true);

    private final boolean proxy;

    PlatformType(boolean proxy) {
        this.proxy = proxy;
    }

    public boolean isProxy() {
        return proxy;
    }
}
