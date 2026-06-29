# task-telemetry

[Versione italiana](README.it.md)

**task-telemetry** is a lightweight Java library for emitting and observing live telemetry from asynchronous or long-running tasks.

It helps a task communicate:

* when it starts;
* its progress;
* informational messages and warnings;
* whether it is still alive;
* how it ended.

The task does not know who is listening. It only emits events. One or more listeners can observe those events through a pluggable transport.

## Why

Backend systems often contain long-running operations:

* scheduled jobs;
* imports and exports;
* background tasks;
* maintenance commands;
* CLI processes;
* async operations triggered by REST APIs.

A common solution is to write status information into logs, files, databases, caches, or custom tables. That works, but it often couples the task to the observer and adds persistence even when only live visibility is needed.

**task-telemetry** provides a small, explicit and framework-agnostic way to publish live task events.

## What it is not

This library is intentionally not:

* a scheduler;
* a job runner;
* a workflow engine;
* a message broker;
* a persistent queue;
* a replacement for Spring Batch, Quartz, JobRunr, Temporal or OpenTelemetry;
* a reactive programming framework;
* a dashboard.

It observes tasks. It does not execute them.

## Features

* Pure Java library.
* Java 17 baseline.
* Maven project.
* No runtime dependencies.
* Framework-agnostic core.
* Spring is not required.
* Explicit builder-based configuration.
* No mandatory annotations.
* No classpath scanning.
* Live best-effort event delivery.
* Automatic heartbeat.
* Listener filtering.
* In-memory transport.
* Localhost socket transport for separate processes.
* `TaskWatcher` helper for high-level consumers.
* Derived task liveness status: `RUNNING`, `STALE`, `LOST`, `COMPLETED`, `FAILED`, `CANCELLED`.

## Core concepts

### Task

A task is a logical type of work.

Examples:

* `IMPORT_CUSTOMERS`
* `EXPORT_REPORTS`
* `FILE_UPLOAD`
* `DATA_MIGRATION_2026`

### Execution

An execution is one concrete run of a task.

Each execution has an `executionId`.

### Correlation key

A correlation key is optional. It links an execution to an application-specific value.

Examples:

* `customer-import-2026.csv`
* `tenant-42`
* `user-1827`
* `case-556101`

### Reporter

A `TaskReporter` is used by the task to emit events.

It is bound to one execution and implements `AutoCloseable`.

### Listener

A listener receives task events.

Listeners can filter by:

* task name;
* execution id;
* correlation key;
* event type.

### Transport

A transport is the channel used to deliver events.

Current transports:

* in-memory transport;
* localhost socket transport.

Future transports may include Redis, RabbitMQ, Kafka, WebSocket/SSE bridges, or HTTP callbacks.

## Event types

| Event       | Meaning                                                    |
| ----------- | ---------------------------------------------------------- |
| `STARTED`   | The execution has started.                                 |
| `PROGRESS`  | The task reports progress from 0 to 100.                   |
| `INFO`      | Informational message.                                     |
| `WARNING`   | Non-blocking warning.                                      |
| `HEARTBEAT` | The task is still alive.                                   |
| `COMPLETED` | The task ended successfully.                               |
| `FAILED`    | The task ended with an error.                              |
| `CANCELLED` | The task was cancelled or closed without a terminal event. |

## Event model

Each event contains:

* `eventId`;
* `taskName`;
* `executionId`;
* optional `correlationKey`;
* `eventType`;
* `timestamp`;
* `sequenceNumber`;
* optional `message`;
* optional `progress`.

Events only carry scalar data plus `message` and `progress`.

There is no arbitrary object payload. This keeps the event model consistent across in-memory and cross-process transports.

## Installation

This project currently targets Java 17.

If the artifact is not published to a public Maven repository yet, build and install it locally:

```bash
mvn clean install
```

Then use it from another Maven project:

```xml
<dependency>
    <groupId>org.tasktelemetry</groupId>
    <artifactId>task-telemetry</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick start

### Create a telemetry runtime

```java
try (TaskTelemetry telemetry = TaskTelemetry.defaults()) {
    // use telemetry here
}
```

`TaskTelemetry.defaults()` creates a ready-to-use runtime with an in-memory transport and automatic heartbeat.

### Emit events from a task

```java
try (TaskReporter reporter = telemetry.start("IMPORT_CUSTOMERS", "customers.csv")) {
    reporter.progress(0, "Import started");
    reporter.info("Reading input file");
    reporter.progress(50, "Halfway done");
    reporter.warning("Row 42 was skipped");
    reporter.progress(100, "Import completed");

    reporter.completed("Import successful");
}
```

When the reporter is opened, a `STARTED` event is emitted automatically.

If the reporter is closed without a terminal event, the default behavior is to emit `CANCELLED`.

### Listen to events

```java
ListenerHandle handle = telemetry.listen()
        .taskName("IMPORT_CUSTOMERS")
        .onEvent(event -> {
            System.out.println(event.type() + " " + event.message());

            if (event.progress() != null) {
                updateProgressBar(event.progress());
            }
        })
        .start();

// later
handle.stop();
```

## Automatic heartbeat

A task can stay silent for a while even if it is still running.

For this reason, `task-telemetry` can automatically emit `HEARTBEAT` events while the reporter is open.

Normal events such as `PROGRESS`, `INFO` or `WARNING` also count as life signals.

Basic rule:

```text
TaskReporter open   -> execution is considered alive
TaskReporter closed -> heartbeat stops
JVM dead            -> heartbeat stops
```

The heartbeat interval can be configured:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .heartbeatInterval(Duration.ofSeconds(2))
        .build();
```

## TaskWatcher

`TaskWatcher` is a higher-level helper for consumers.

It is useful when the consumer simply wants to:

* wait until a task appears;
* receive progress updates;
* react to heartbeat events;
* wait until the task completes or is considered lost.

```java
try (TaskWatcher watcher = new TaskWatcher(transport, "FILE_UPLOAD")) {
    watcher.onProgress(percent -> updateProgressBar(percent));
    watcher.onHeartbeat(() -> log("task is still alive"));

    if (!watcher.awaitStart(Duration.ofSeconds(5))) {
        return;
    }

    TaskExecutionStatus status = watcher.awaitCompletion();

    System.out.println("Final status: " + status);
}
```

Possible final statuses:

* `COMPLETED`
* `FAILED`
* `CANCELLED`
* `LOST`

## Liveness status

A consumer can derive the current status of a task execution from the latest received event.

Supported statuses:

| Status      | Meaning                                                 |
| ----------- | ------------------------------------------------------- |
| `RUNNING`   | Events are being received.                              |
| `STALE`     | No events have been received for longer than expected.  |
| `LOST`      | The execution is probably dead or no longer observable. |
| `COMPLETED` | The task completed successfully.                        |
| `FAILED`    | The task failed.                                        |
| `CANCELLED` | The task was cancelled.                                 |

Example thresholds:

```text
heartbeatInterval = 5 seconds
staleAfter        = 15 seconds
lostAfter         = 60 seconds
```

Interpretation:

```text
last event 8s ago  -> RUNNING
last event 20s ago -> STALE
last event 70s ago -> LOST
```

## Same JVM vs separate processes

Producer and consumer communicate only if they share the same transport.

### Same JVM

Use the same `TaskTelemetry` instance or the same in-memory transport.

### Separate processes on the same machine

Use the localhost socket transport.

The model has two actors:

* the task process acts as a server;
* the client process connects and receives events from that moment on.

There is no broker and no event replay.

Events emitted before the client connects are not recovered.

## Delivery semantics

The first goal of the library is live observability, not guaranteed delivery.

Current semantics:

```text
best-effort, live, non-persistent
```

Consequences:

* if nobody is listening, the event may be lost;
* if a listener starts later, it does not receive past events;
* if a transport fails, events may be lost;
* there is no exactly-once guarantee;
* there is no persistent retry mechanism.

This is intentional.

## Error handling

By default, if event publishing fails, the main task should not fail.

Available policies:

* ignore publishing failures;
* log publishing failures;
* rethrow publishing failures.

Example:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .errorHandler(TaskTelemetryErrorHandler.logging())
        .build();
```

## Configuration

Configuration is explicit and builder-based.

Examples:

```java
TaskTelemetry telemetry = TaskTelemetry.builder()
        .transport(new InMemoryTaskTransport())
        .heartbeatInterval(Duration.ofSeconds(5))
        .closeBehavior(TaskReporter.CloseBehavior.CANCELLED)
        .logPrefix("task-telemetry -")
        .errorHandler(TaskTelemetryErrorHandler.logging())
        .build();
```

There are no mandatory annotations and no external configuration files.

## Design principles

The project follows a few strict design rules:

* keep the core small;
* keep the API explicit;
* avoid magic;
* avoid framework coupling;
* avoid persistence in the core;
* do not execute tasks;
* do not schedule tasks;
* do not introduce bidirectional commands in the first version;
* keep transports pluggable.

## Roadmap

Possible future extensions:

* Spring Boot starter;
* Redis Pub/Sub transport;
* RabbitMQ transport;
* Kafka transport;
* WebSocket/SSE bridge for UIs;
* optional REST bridge;
* optional dashboard;
* optional storage for last known status;
* optional OpenTelemetry integration.

All of these should remain optional.

## Current status

The project is under active development.

Implemented so far:

* core API;
* `TaskReporter`;
* `TaskTelemetry`;
* immutable `TaskEvent`;
* event types;
* automatic heartbeat;
* listener filtering;
* in-memory transport;
* localhost socket transport;
* `TaskWatcher`;
* liveness monitoring;
* Java examples;
* unit and integration tests.

API stability is not guaranteed yet.

## License

A license file should be added before publishing stable releases.

Until a license is explicitly provided, public visibility does not automatically mean that the code can be reused freely.
