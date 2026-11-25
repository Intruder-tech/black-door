package io.github.intruder_tech.blackdoor.common.time;

enum SystemTimeSource implements TimeSource {
    INSTANCE;

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
