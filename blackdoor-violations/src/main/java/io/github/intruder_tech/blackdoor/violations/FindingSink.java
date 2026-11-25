package io.github.intruder_tech.blackdoor.violations;

/**
 * Receives findings. Called on network threads: implementations must never block and must
 * bound any memory they keep.
 */
@FunctionalInterface
public interface FindingSink {

    void accept(Finding finding);
}
