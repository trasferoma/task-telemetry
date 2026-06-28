package org.tasktelemetry.event;

/**
 * Identity of a single task execution, shared by every event it emits.
 *
 * <p>{@code taskName} identifies the type of task; {@code executionId} identifies
 * the single run. {@code correlationKey} is optional and links the execution to
 * an application domain (for example a business entity or an input file); it may
 * be {@code null}.
 *
 * @param taskName       type of task, required
 * @param executionId    identifier of the single execution, required
 * @param correlationKey optional link to an application domain, may be {@code null}
 */
public record TaskExecutionDescriptor(
        String taskName,
        String executionId,
        String correlationKey) {

    public TaskExecutionDescriptor {
        requireText(taskName, "taskName");
        requireText(executionId, "executionId");
    }

    /**
     * Creates a descriptor without a correlation key.
     *
     * @param taskName    type of task, required
     * @param executionId identifier of the single execution, required
     * @return the descriptor
     */
    public static TaskExecutionDescriptor of(String taskName, String executionId) {
        return new TaskExecutionDescriptor(taskName, executionId, null);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }
}
