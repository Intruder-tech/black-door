package io.github.intruder_tech.blackdoor.common.log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordingLogger implements BdLogger {
    final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void debug(String message) {
        lines.add("DEBUG " + message);
    }

    @Override
    public void info(String message) {
        lines.add("INFO " + message);
    }

    @Override
    public void warn(String message) {
        lines.add("WARN " + message);
    }

    @Override
    public void error(String message, Throwable error) {
        lines.add("ERROR " + message);
    }
}
