# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a **greenfield project**. The only meaningful artifact today is `SPEC.md`; the Java source is an IntelliJ stub (`src/main/java/org/tasktelemetry/Main.java`) with no library code, no tests, and no Maven modules yet. `SPEC.md` is the source of truth and drives implementation — read it before designing or coding anything.

`task-telemetry` is a pure-Java, framework-agnostic library that lets any async/long-running process emit live progress telemetry to one or more listeners. It is **not** a scheduler, job runner, message broker, or reactive library — it only emits and listens to events produced by application code.

## Build & run

Java 17, Maven. No Maven wrapper is committed, so use a locally installed `mvn`.

```bash
mvn compile          # compile
mvn test             # run all tests (none exist yet)
mvn -Dtest=ClassName test                 # run a single test class
mvn -Dtest=ClassName#methodName test      # run a single test method
mvn package          # build artifact(s)
```

The current `pom.xml` is a single module. `SPEC.md` §14 calls for a multi-module layout (`core`, `transport-inmemory`, `transport-local-socket`, `spring-boot-starter`, `examples`) — converting to a parent POM with modules is expected as part of implementation, not a deviation.

## Architecture (target, per SPEC.md)

Event flow — the task never knows who listens:

```
Async task -> TaskReporter -> TaskEvent -> TaskTransport -> TaskListener(s)
```

Core types (SPEC §6, §14.1, §22): `TaskTelemetry` + builder, `TaskReporter` (AutoCloseable, bound to one execution), `TaskEvent` (immutable), `TaskEventType`, `TaskListener` (functional), `TaskTransport` (pluggable SPI), execution descriptor, heartbeat scheduler, listener filtering, close policy, `TaskTelemetryErrorHandler` (publish-failure policy) and `TaskTelemetryLogger` (logging abstraction, default `JulTaskTelemetryLogger` over `java.util.logging`).

Key model facts:
- A **task** (`taskName`) is a type; each run is an **execution** (`executionId`, UUID). Optional `correlationKey` links to an application domain.
- `TaskEvent` carries at least: eventId, taskName, executionId, optional correlationKey, eventType, timestamp (library-generated), sequenceNumber (monotonic per execution), optional message/progress. There is **no** arbitrary `Object payload`: it was removed because it only survived the in-memory transport and was dropped cross-process. The event carries only scalar data + message + progress, uniform on every transport.
- Event types: `STARTED, PROGRESS, INFO, WARNING, HEARTBEAT, COMPLETED, FAILED, CANCELLED`. `FAILED` carries only its `message` (= `Throwable.toString()`); no structured failure object, no stack trace.

## Implementation order (SPEC §29)

Build incrementally, core first, in this order: `TaskEventType` → immutable `TaskEvent` → execution descriptor → `TaskTransport` → `TaskListener` → `TaskReporter` → `TaskTelemetry` + builder → automatic heartbeat → in-memory transport → listener filtering → unit tests → pure-Java example. Stabilize core + tests before anything else.

## Non-negotiable constraints

These are explicit decisions in `SPEC.md` (§4, §27) — do not violate them:
- **Core has zero Spring dependency.** Spring Boot support is a future, optional module only.
- **No annotations, reflection, or classpath scanning.** Configuration is via Java builder only; no config files.
- Task communicates **outbound only** — no bidirectional ping, remote commands, pause/resume/cancel, or delivery ACKs in v1.
- Delivery is **best-effort, live, non-persistent**: no exactly-once, no retry queues, no mandatory storage, no dashboard.
- Heartbeat is automatic and tied to `TaskReporter` lifecycle: reporter open → execution alive; close → heartbeat stops. Default `close()` without a terminal event emits `CANCELLED`.
- Keep dependencies minimal (JDK; JUnit 5 + AssertJ for tests). The in-memory transport must **not** serialize; a `TaskEventSerializer` SPI is introduced only when the socket transport arrives.
- Logging goes through the `TaskTelemetryLogger` abstraction (pluggable for Log4j/SLF4J later); the core default is `JulTaskTelemetryLogger` (`java.util.logging`, no runtime dependency). The log message prefix is configurable **via the builder only** — `TaskTelemetry.builder().logPrefix("...")`, default `task-telemetry -`, empty string disables it — **no config file** (SPEC §18.1).

## Threading & testing requirements

- Prefer a single shared `ScheduledExecutorService` per `TaskTelemetry` instance for heartbeats (not a thread per task); recognizable, daemon (or configurable) thread names like `task-telemetry-heartbeat-1`.
- Inject the clock and make the heartbeat scheduler testable — avoid real `sleep` in tests; keep tests deterministic. See `SPEC.md` §20 for the required test list (start/progress/completed/failed emit correct events, close stops heartbeat, etc.).

## Notes

- `SPEC.md` §28 lists open questions (close policy default, progress model, sync vs async dispatch). When a decision is needed and unresolved, surface it rather than silently picking one.
- `.junie/plans/` is an empty IDE-assistant directory; ignore it.
