package org.tasktelemetry.monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.tasktelemetry.event.TaskEvent;
import org.tasktelemetry.event.TaskEventType;
import org.tasktelemetry.listener.TaskListener;

/**
 * Listener that derives a live {@link TaskExecutionStatus} for each observed
 * execution from the events it receives (see SPEC §9.1).
 *
 * <p>Any received event counts as a liveness signal and refreshes the
 * "last seen" instant. While an execution keeps sending events it is
 * {@link TaskExecutionStatus#RUNNING}; after {@code staleAfter} of silence it
 * becomes {@link TaskExecutionStatus#STALE}; after {@code lostAfter} it becomes
 * {@link TaskExecutionStatus#LOST}. A terminal event freezes the status to
 * {@link TaskExecutionStatus#COMPLETED}, {@link TaskExecutionStatus#FAILED} or
 * {@link TaskExecutionStatus#CANCELLED}.
 *
 * <p>The instant of each event is taken from the injected {@link Clock} when the
 * event is received, so the status reflects the consumer's view and does not
 * depend on the producer's clock. Instances are safe for concurrent use.
 */
public final class TaskHeartbeatMonitor implements TaskListener {

    private static final Duration DEFAULT_STALE_AFTER = Duration.ofSeconds(15);
    private static final Duration DEFAULT_LOST_AFTER = Duration.ofSeconds(60);

    private final Clock clock;
    private final Duration staleAfter;
    private final Duration lostAfter;
    private final ConcurrentMap<String, ExecutionState> states = new ConcurrentHashMap<>();

    /**
     * Creates a monitor with the default thresholds (stale after 15 seconds, lost
     * after 60 seconds).
     *
     * @param clock clock used to measure silence, required
     */
    public TaskHeartbeatMonitor(Clock clock) {
        this(clock, DEFAULT_STALE_AFTER, DEFAULT_LOST_AFTER);
    }

    /**
     * Creates a monitor with explicit thresholds.
     *
     * @param clock      clock used to measure silence, required
     * @param staleAfter silence after which an execution is considered stale, must be positive
     * @param lostAfter  silence after which an execution is considered lost, must be greater than {@code staleAfter}
     */
    public TaskHeartbeatMonitor(Clock clock, Duration staleAfter, Duration lostAfter) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter must not be null");
        this.lostAfter = Objects.requireNonNull(lostAfter, "lostAfter must not be null");
        requireOrderedThresholds(staleAfter, lostAfter);
    }

    @Override
    public void onEvent(TaskEvent event) {
        Instant now = clock.instant();
        TaskExecutionStatus terminalStatus = terminalStatusOf(event.type());

        states.compute(event.executionId(), (id, existing) -> {
            if (existing != null && existing.terminalStatus() != null) {
                return existing;
            }
            return new ExecutionState(now, terminalStatus);
        });
    }

    /**
     * Tells whether any event has been received for the given execution.
     *
     * @param executionId the execution to check
     * @return {@code true} if the execution is being tracked
     */
    public boolean isTracking(String executionId) {
        return states.containsKey(executionId);
    }

    /**
     * Returns the current status of the given execution.
     *
     * @param executionId the execution to inspect
     * @return the derived status
     * @throws IllegalArgumentException if no event has been received for the execution
     */
    public TaskExecutionStatus statusOf(String executionId) {
        ExecutionState state = states.get(executionId);
        if (state == null) {
            throw new IllegalArgumentException("Unknown execution: " + executionId);
        }
        if (state.terminalStatus() != null) {
            return state.terminalStatus();
        }
        return deriveLiveStatus(state.lastSeen());
    }

    private TaskExecutionStatus deriveLiveStatus(Instant lastSeen) {
        Duration elapsed = Duration.between(lastSeen, clock.instant());
        if (elapsed.compareTo(lostAfter) >= 0) {
            return TaskExecutionStatus.LOST;
        }
        if (elapsed.compareTo(staleAfter) >= 0) {
            return TaskExecutionStatus.STALE;
        }
        return TaskExecutionStatus.RUNNING;
    }

    private static TaskExecutionStatus terminalStatusOf(TaskEventType type) {
        return switch (type) {
            case COMPLETED -> TaskExecutionStatus.COMPLETED;
            case FAILED -> TaskExecutionStatus.FAILED;
            case CANCELLED -> TaskExecutionStatus.CANCELLED;
            default -> null;
        };
    }

    private static void requireOrderedThresholds(Duration staleAfter, Duration lostAfter) {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must be positive: " + staleAfter);
        }
        if (lostAfter.compareTo(staleAfter) <= 0) {
            throw new IllegalArgumentException(
                    "lostAfter must be greater than staleAfter: " + lostAfter + " <= " + staleAfter);
        }
    }

    private record ExecutionState(Instant lastSeen, TaskExecutionStatus terminalStatus) {
    }
}
