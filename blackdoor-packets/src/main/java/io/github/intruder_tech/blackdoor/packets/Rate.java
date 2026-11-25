package io.github.intruder_tech.blackdoor.packets;

/** Token-bucket parameters: burst capacity and sustained packets per second. */
public record Rate(int burst, double perSecond) {
}
