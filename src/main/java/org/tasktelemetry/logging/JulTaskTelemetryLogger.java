package org.tasktelemetry.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link TaskTelemetryLogger} backed by {@code java.util.logging}, so
 * the core keeps no external logging dependency.
 *
 * <p>Every message is prefixed with the configured {@code log-prefix} so all
 * task-telemetry log lines start with the same marker.
 */
public final class JulTaskTelemetryLogger implements TaskTelemetryLogger {

    /** Marker prepended to every message when no prefix is configured. */
    public static final String DEFAULT_LOG_PREFIX = "task-telemetry -";

    private static final String LOGGER_NAME = "org.tasktelemetry";

    private final Logger logger;
    private final String prefix;

    /**
     * Creates a logger writing to the {@code org.tasktelemetry} JUL logger.
     *
     * @param prefix the marker prepended to every message; {@code null} is
     *               treated as no prefix
     */
    public JulTaskTelemetryLogger(String prefix) {
        this(Logger.getLogger(LOGGER_NAME), prefix);
    }

    JulTaskTelemetryLogger(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix == null ? "" : prefix;
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, () -> applyPrefix(message));
    }

    @Override
    public void warning(String message) {
        logger.log(Level.WARNING, () -> applyPrefix(message));
    }

    @Override
    public void warning(String message, Throwable error) {
        logger.log(Level.WARNING, error, () -> applyPrefix(message));
    }

    @Override
    public void error(String message) {
        logger.log(Level.SEVERE, () -> applyPrefix(message));
    }

    @Override
    public void error(String message, Throwable error) {
        logger.log(Level.SEVERE, error, () -> applyPrefix(message));
    }

    private String applyPrefix(String message) {
        if (prefix.isEmpty()) {
            return message;
        }
        return prefix + " " + message;
    }
}