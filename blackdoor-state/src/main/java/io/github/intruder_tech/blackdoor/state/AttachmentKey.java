package io.github.intruder_tech.blackdoor.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Typed slot for per-session data owned by other modules (for example the packet engine's rate
 * limiters), so they do not need parallel maps keyed by connection. Bounded: at most
 * {@link #MAX_KEYS} keys can ever exist.
 */
public final class AttachmentKey<T> {

    public static final int MAX_KEYS = 16;
    private static final AtomicInteger NEXT = new AtomicInteger();

    private final String name;
    private final int index;

    private AttachmentKey(String name, int index) {
        this.name = name;
        this.index = index;
    }

    /** Creates a key. Call once per kind of attachment and keep it in a static field. */
    public static <T> AttachmentKey<T> create(String name) {
        int index = NEXT.getAndIncrement();
        if (index >= MAX_KEYS) {
            throw new IllegalStateException("too many attachment keys");
        }
        return new AttachmentKey<>(Objects.requireNonNull(name, "name"), index);
    }

    int index() {
        return index;
    }

    @Override
    public String toString() {
        return name;
    }
}
