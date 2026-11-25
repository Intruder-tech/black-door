package io.github.intruder_tech.blackdoor.core.command;

import java.time.Duration;
import java.util.function.Predicate;

public final class AwaitMessages {

    private AwaitMessages() {
    }

    public static void await(RecordingSender sender, Predicate<String> match) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (sender.messages.stream().anyMatch(match)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("expected message not received; got: " + sender.messages);
    }
}
