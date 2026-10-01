/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpJsonObject;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpTask;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Process-local catalogue of existing workers. This class never schedules or starts work.
 * <p>
 * Each entry is read two ways: as an operation, the snapshot {@code operations_status} renders, and as
 * an MCP task ({@link #task}), with the operationId as the taskId. Both views read the same entry, so a
 * client following a task and one polling {@code operations_status} see the same attempt.
 */
public final class McpOperationRegistry {

    /**
     * How long a terminal attempt stays readable. The same window the jobs behind it keep their
     * outcomes for, and deliberately the same constant rather than a second hour that happens to
     * agree: a job outliving the entry that names it makes {@code register} refuse a retry that the
     * tool descriptions promise works.
     */
    public static final Duration RETENTION = BoundedJobs.COMPLETED_RETENTION;

    /** How often a client following an operation as a task is asked to come back. */
    public static final Duration TASK_POLL_INTERVAL = Duration.ofSeconds(5);

    /** What a task says while its operation waits for a slot, or winds down after a cancellation. */
    private static final Map<OperationState, String> WORKING_MESSAGES = Map.of(
            OperationState.QUEUED, "queued",
            OperationState.CANCEL_REQUESTED, "cancellation requested");

    private static final String OPERATIONS_STATUS = "operations_status";
    private static final String RECORDINGS_ANALYZE_RECORDING = "recordings_analyzeRecording";
    private static final String OPERATION_ID = "operationId";
    private static final String RECORDING_ID = "recordingId";
    private static final String RETRY = "retry";
    private static final String POLL_WHY = "reports this operation's progress, and its result once it finishes";
    private static final String RETRY_WHY = "starts a new attempt at analysing the recording";
    private static final String CANCEL_PENDING =
            "A cancellation request remains pending until the worker exits.";
    private static final String NO_ROLLBACK = "Cancellation does not roll back files or reports already written.";
    private static final String COMPLETED_RETAINED =
            "The operation completed. Its status is retained in this process for one hour.";

    private static final String CANCELLED_MESSAGE = "cancelled";
    private static final String FAILED_WITHOUT_CAUSE = "The operation failed without a cause";
    private static final String NOT_FINISHED = "An operation still in progress has no terminal state: ";

    private final Map<String, Entry<?>> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong registrationSequence = new AtomicLong();

    public McpOperationRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public <V> String register(OperationKind kind, OperationHandle<V> handle, Function<V, ? extends Record> result) {
        return register(kind, handle, result, (Supplier<String>) null);
    }

    public <V> String register(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Supplier<String> recordingIdentity) {
        return register(kind, handle, result, recordingIdentity, snapshotAnswer(), null);
    }

    /**
     * @param answer what a client following this operation as a task is answered with once it has
     *               completed -- the answer a caller that waited would have got. Computed once, when the
     *               operation is first seen finished; without one, the answer is the terminal snapshot
     */
    public <V> String register(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Function<V, McpToolResult> answer) {
        return register(kind, handle, result, null, valueAnswer(answer), null);
    }

    public <V> String register(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Supplier<String> recordingIdentity,
            Function<V, McpToolResult> answer) {
        return register(kind, handle, result, recordingIdentity, valueAnswer(answer), null);
    }

    /**
     * @param retry the call that starts a new attempt with the arguments this one ran with, which a
     *              failed or cancelled attempt's {@code followUp} names instead of the kind's retry
     *              sentence
     */
    public <V> String register(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Function<V, McpToolResult> answer,
            McpNextTool retry) {
        return register(kind, handle, result, null, valueAnswer(answer), Objects.requireNonNull(retry, "retry"));
    }

    /**
     * Registers the operation unless it finished longer ago than {@link #RETENTION}.
     *
     * @param retry the call that starts a new attempt, as {@link #register(OperationKind, OperationHandle,
     *              Function, Function, McpNextTool)} takes it
     */
    public <V> Optional<String> registerIfRetained(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Function<V, McpToolResult> answer,
            McpNextTool retry) {
        return registerIfRetained(kind, handle, result, null, valueAnswer(answer),
                Objects.requireNonNull(retry, "retry"));
    }

    private <V> String register(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Supplier<String> recordingIdentity,
            TerminalAnswer<V> answer,
            McpNextTool retry) {
        return registerIfRetained(kind, handle, result, recordingIdentity, answer, retry)
                .orElseThrow(() -> new IllegalArgumentException("Operation retention has expired"));
    }

    private <V> Optional<String> registerIfRetained(
            OperationKind kind,
            OperationHandle<V> handle,
            Function<V, ? extends Record> result,
            Supplier<String> recordingIdentity,
            TerminalAnswer<V> answer,
            McpNextTool retry) {
        Objects.requireNonNull(kind, "kind");
        evictExpired();
        Instant finished = handle.finishedAt();
        if (finished != null && finished.isBefore(clock.instant().minus(RETENTION))) {
            return Optional.empty();
        }
        String id = handle.operationId();
        var entry = new Entry<>(
                kind, handle, result, answer, recordingIdentity, retry, registrationSequence.incrementAndGet());
        entries.putIfAbsent(id, entry);
        return Optional.of(id);
    }

    public Optional<String> latestForRecording(String recordingId) {
        evictExpired();
        return entries.values().stream()
                .filter(entry -> entry.kind.tracksRecording())
                .filter(entry -> entry.recordingIdentity != null
                        && recordingId.equals(entry.recordingIdentity.get()))
                .max(Comparator.<Entry<?>, Instant>comparing(entry -> entry.startedAt)
                        .thenComparingLong(entry -> entry.registrationSequence)).map(entry -> entry.operationId);
    }

    public Snapshot status(String operationId) {
        return status(operationId, kind -> true);
    }

    public Snapshot status(String operationId, Predicate<OperationKind> allowedKind) {
        return require(operationId, allowedKind).snapshot();
    }

    public Snapshot cancel(String operationId, Predicate<OperationKind> allowedKind) {
        Entry<?> entry = require(operationId, allowedKind);
        entry.cancel();
        return entry.snapshot(false);
    }

    /**
     * The operation as an MCP task: the operationId is the taskId, the operation's start and finish are
     * the task's creation and last update, and the task stays readable for {@link #RETENTION}, the
     * window the operation itself is kept for.
     *
     * @param allowedKind the same gate {@code operations_status} applies: a kind it refuses is unknown
     * @throws IllegalArgumentException when no operation has this id, it has expired, or its kind is refused
     */
    public McpTask task(String operationId, Predicate<OperationKind> allowedKind) {
        TaskView view = require(operationId, allowedKind).taskView();
        Snapshot snapshot = view.snapshot();
        long lastUpdated = snapshot.finishedAtEpochMs() == null
                ? snapshot.startedAtEpochMs()
                : snapshot.finishedAtEpochMs();
        return new McpTask(snapshot.operationId(), view.state(), Instant.ofEpochMilli(snapshot.startedAtEpochMs()),
                Instant.ofEpochMilli(lastUpdated), RETENTION, TASK_POLL_INTERVAL);
    }

    private Entry<?> require(String id, Predicate<OperationKind> allowedKind) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("operationId is required");
        }
        evictExpired();
        Entry<?> entry = entries.get(id.trim());
        if (entry == null || !allowedKind.test(entry.kind)) {
            throw new IllegalArgumentException("Unknown or expired operation: " + id
                    + ". Operations are process-local and retained for one hour after completion.");
        }
        return entry;
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(RETENTION);
        entries.values().removeIf(entry -> {
            Instant finished = entry.finishedAt();
            return finished != null && finished.isBefore(cutoff);
        });
    }

    /**
     * One observation of one operation: what {@code operations_status} and {@code operations_cancel}
     * answer, and what the family tools carry beside their own answer as {@code operation}.
     *
     * @param result what the operation produced, once it completed: shaped by the kind that started it,
     *               so an open object -- a recording import names its profile, a hub download its
     *               recording
     */
    public record Snapshot(
            String operationId,
            OperationKind kind,
            OperationState status,
            @McpDescription("When the attempt started, as UTC epoch milliseconds")
            long startedAtEpochMs,
            @McpNullable
            @McpDescription("When the attempt finished, as UTC epoch milliseconds; null while it runs")
            Long finishedAtEpochMs,
            boolean cancellationRequested,
            @McpDescription("Whether the attempt failed or was cancelled, so a new one may be started")
            boolean retryable,
            Progress progress,
            @McpNullable
            @McpDescription("What the operation produced; null until it completes. Its fields depend on the kind")
            McpJsonObject result,
            @McpNullable
            Failure error,
            McpFollowUp followUp) {

        /**
         * This observation without what the operation produced: for an answer that already carries the
         * result as its own payload and names the operation only as the attempt behind it.
         */
        public Snapshot withoutResult() {
            return new Snapshot(operationId, kind, status, startedAtEpochMs, finishedAtEpochMs,
                    cancellationRequested, retryable, progress, null, error, followUp);
        }
    }

    /**
     * @param phase   which step of its work the operation is on
     * @param details what it has to say about that step
     */
    public record Progress(OperationPhase phase, OperationDetails details) {
    }

    /** Why an attempt ended without a result. */
    public enum FailureCode {
        /** The attempt was cancelled, by a caller or by shutdown. */
        CANCELLED,
        /** The work itself failed. */
        OPERATION_FAILED
    }

    /**
     * @param message the failure's own message, null when it had none
     */
    public record Failure(FailureCode code, @McpNullable String message) {
    }

    /** One entry read as a task: the snapshot the timing comes from and the state it is in. */
    private record TaskView(Snapshot snapshot, McpTaskState state) {
    }

    /** What a completed operation answers a client following it as a task. */
    @FunctionalInterface
    private interface TerminalAnswer<V> {

        McpToolResult answer(V value, Snapshot terminal);
    }

    /**
     * The default answer: the terminal snapshot, typed, exactly as {@code operations_status} answers it
     * - the record as structured content and as its JSON text.
     */
    private static <V> TerminalAnswer<V> snapshotAnswer() {
        return (value, terminal) -> McpToolResult.of(terminal);
    }

    /**
     * The progress a worker published, in the one shape every kind reports: the MCP jobs publish it
     * typed, and the profile pipeline publishes its own progress, whose stages are read from it.
     */
    private static OperationDetails details(Object progress) {
        return switch (progress) {
            case null -> OperationDetails.NONE;
            case OperationDetails details -> details;
            case PipelineProgress pipeline -> OperationDetails.pipeline(pipeline);
            default -> throw new IllegalStateException(
                    "An operation published progress of an unknown type: type=" + progress.getClass().getName());
        };
    }

    /**
     * The completed result as the open object the snapshot carries: the record the kind's renderer
     * made of it. A record always serialises as an object, which is why the renderer must return one.
     */
    private static McpJsonObject result(Record rendered) {
        return new McpJsonObject((ObjectNode) Json.toTree(rendered));
    }

    private static <V> TerminalAnswer<V> valueAnswer(Function<V, McpToolResult> answer) {
        Objects.requireNonNull(answer, "answer");
        return (value, terminal) -> answer.apply(value);
    }

    private static McpTaskState.Working working(Snapshot snapshot) {
        String message = WORKING_MESSAGES.get(snapshot.status());
        return new McpTaskState.Working(message != null ? message : snapshot.progress().phase().code());
    }

    private static final class Entry<V> {
        private final OperationKind kind;
        private final String operationId;
        private final long registrationSequence;
        private final Instant startedAt;
        private final Supplier<String> recordingIdentity;
        /** The call that starts a new attempt, when the tool that started this one named it. */
        private final McpNextTool retry;
        private volatile OperationHandle<V> handle;
        private Function<V, ? extends Record> renderer;
        private TerminalAnswer<V> answer;
        private volatile Snapshot terminal;
        /** Frozen with {@link #terminal}, from the same observation, and never recomputed. */
        private volatile McpTaskState terminalState;

        private Entry(
                OperationKind kind,
                OperationHandle<V> handle,
                Function<V, ? extends Record> renderer,
                TerminalAnswer<V> answer,
                Supplier<String> recordingIdentity,
                McpNextTool retry,
                long registrationSequence) {
            this.registrationSequence = registrationSequence;
            this.retry = retry;
            this.kind = kind;
            this.operationId = handle.operationId();
            this.startedAt = handle.startedAt();
            this.recordingIdentity = recordingIdentity;
            this.handle = handle;
            this.renderer = renderer;
            this.answer = answer;
        }

        /**
         * The entry as a task. A full snapshot is taken first, which freezes the terminal state the
         * first time the operation is seen finished; until then the task is working.
         */
        private TaskView taskView() {
            Snapshot current = snapshot();
            synchronized (this) {
                if (terminalState != null) {
                    return new TaskView(terminal, terminalState);
                }
            }
            return new TaskView(current, working(current));
        }

        /**
         * Where a finished operation leaves its task. Runs once per entry, under the entry's lock, while
         * the value is still held: the answer is computed exactly once, and one that throws is the
         * tool's failure, as it would have been for a caller that waited.
         */
        private McpTaskState terminalState(
                OperationSnapshot<V> source, Snapshot snapshot, TerminalAnswer<V> terminalAnswer) {
            return switch (source.state()) {
                case COMPLETED -> answered(source.result(), snapshot, terminalAnswer);
                case CANCELLED -> new McpTaskState.Cancelled(CANCELLED_MESSAGE);
                case FAILED -> new McpTaskState.ToolFailed(source.failure() == null
                        ? new IllegalStateException(FAILED_WITHOUT_CAUSE)
                        : source.failure());
                case QUEUED, RUNNING, CANCEL_REQUESTED ->
                        throw new IllegalStateException(NOT_FINISHED + source.state().code());
            };
        }

        private static <V> McpTaskState answered(V value, Snapshot snapshot, TerminalAnswer<V> terminalAnswer) {
            try {
                return new McpTaskState.Completed(terminalAnswer.answer(value, snapshot));
            } catch (RuntimeException e) {
                return new McpTaskState.ToolFailed(e);
            }
        }

        private Instant finishedAt() {
            OperationHandle<V> current = handle;
            if (current != null) {
                return current.finishedAt();
            }
            Long finished = terminal.finishedAtEpochMs();
            return finished == null ? null : Instant.ofEpochMilli(finished);
        }

        private void cancel() {
            OperationHandle<V> current = handle;
            if (current != null) {
                current.cancel();
            }
        }

        private Snapshot snapshot() {
            return snapshot(true);
        }

        /**
         * What to do next with this attempt. Built without a family gate: {@code operations_status} is
         * served wherever an operation can be read, and an operation is readable only where the family
         * that started it -- and so retries it -- is served.
         */
        private McpFollowUp followUp(OperationState state, boolean retryable) {
            List<McpNextTool> nextTools = new ArrayList<>();
            List<String> guidance = new ArrayList<>();
            if (!state.terminal()) {
                nextTools.add(NextCalls.to(OPERATIONS_STATUS).with(OPERATION_ID, operationId).why(POLL_WHY));
                if (state == OperationState.CANCEL_REQUESTED) {
                    guidance.add(CANCEL_PENDING);
                }
            } else if (retryable) {
                String recordingId = kind.tracksRecording() && recordingIdentity != null ? recordingIdentity.get() : null;
                if (recordingId != null) {
                    nextTools.add(NextCalls.to(RECORDINGS_ANALYZE_RECORDING)
                            .with(RECORDING_ID, recordingId).with(RETRY, true).why(RETRY_WHY));
                } else if (retry != null) {
                    nextTools.add(retry);
                } else {
                    guidance.add(kind.retryInstruction());
                }
                guidance.add(NO_ROLLBACK);
            } else {
                guidance.add(COMPLETED_RETAINED);
            }
            return new McpFollowUp(nextTools, guidance);
        }

        private Snapshot snapshot(boolean includeProgress) {
            OperationHandle<V> current;
            Function<V, ? extends Record> currentRenderer;
            TerminalAnswer<V> currentAnswer;
            synchronized (this) {
                if (terminal != null) {
                    return terminal;
                }
                current = handle;
                currentRenderer = renderer;
                currentAnswer = answer;
            }
            OperationSnapshot<V> source = includeProgress ? current.snapshot() : current.lifecycleSnapshot();
            boolean retryable = source.state() == OperationState.FAILED || source.state() == OperationState.CANCELLED;
            Failure error = source.failure() == null ? null : new Failure(
                    source.state() == OperationState.CANCELLED ? FailureCode.CANCELLED : FailureCode.OPERATION_FAILED,
                    source.failure().getMessage());
            Snapshot snapshot = new Snapshot(source.operationId(), kind, source.state(),
                    source.startedAt().toEpochMilli(),
                    source.finishedAt() == null ? null : source.finishedAt().toEpochMilli(),
                    source.cancellationRequested(), retryable,
                    new Progress(OperationPhase.ofCode(source.phase()), details(source.progress())),
                    source.result() == null ? null : result(currentRenderer.apply(source.result())),
                    error, followUp(source.state(), retryable));
            synchronized (this) {
                if (terminal != null) {
                    return terminal;
                }
                if (includeProgress && source.state().terminal()) {
                    terminal = snapshot;
                    terminalState = terminalState(source, snapshot, currentAnswer);
                    handle = null;
                    renderer = null;
                    answer = null;
                }
                return snapshot;
            }
        }

    }
}
