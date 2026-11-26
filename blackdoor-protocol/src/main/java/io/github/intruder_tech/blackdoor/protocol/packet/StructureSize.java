package io.github.intruder_tech.blackdoor.protocol.packet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Shape of a client-supplied tree such as item data: how deeply it nests and how many elements it
 * has. Measuring never recurses and stops at {@link #CAP}, so hostile data cannot exhaust the
 * stack or the network thread.
 */
public record StructureSize(int depth, int elements) {

    /** Measuring stops after this many elements; values above it are not distinguished. */
    public static final int CAP = 100_000;

    public static final StructureSize NONE = new StructureSize(0, 0);

    public static <T> StructureSize of(T root, Function<? super T, ? extends Iterable<? extends T>> children) {
        if (root == null) {
            return NONE;
        }
        int depth = 1;
        int elements = 1;
        Deque<Iterator<? extends T>> open = new ArrayDeque<>();
        open.push(children.apply(root).iterator());
        while (!open.isEmpty() && elements < CAP) {
            Iterator<? extends T> level = open.peek();
            if (!level.hasNext()) {
                open.pop();
                continue;
            }
            T child = level.next();
            if (child == null) {
                continue;
            }
            elements++;
            depth = Math.max(depth, open.size() + 1);
            open.push(children.apply(child).iterator());
        }
        return new StructureSize(depth, elements);
    }
}
