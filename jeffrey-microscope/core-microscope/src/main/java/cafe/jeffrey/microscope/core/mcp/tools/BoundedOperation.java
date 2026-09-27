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

import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A slow read run the way the writers run theirs: started or joined under a key, waited on for
 * {@link BoundedJobs#WAIT_BUDGET}, and handed back as an {@code operationId} for
 * {@code operations_status} when it takes longer -- or, for a client that declared the tasks
 * extension, waited on for {@link BoundedJobs#TASK_WAIT_BUDGET} and handed back as a task.
 * <p>
 * For the computations a reader opts into — the JMC rule set over a whole recording, an OQL query
 * that builds the dominator tree before it answers — which can run for minutes, well past the point
 * where a client abandons the call and asks again. A call that joins a run in flight gets that run's
 * operation rather than starting a rival, and a failed run is started again by the next call.
 *
 * @param <V> what the work produces, turned into an answer on the caller's thread
 */
public final class BoundedOperation<V> {

    private static final Logger LOG = LoggerFactory.getLogger(BoundedOperation.class);

    private static final String FAILED = "The %s operation failed: %s. Operation: %s";

    /**
     * How much of the failed operation's snapshot the error text carries: half the result cap, so the
     * message around it always fits, and a longer snapshot ends in the TRUNCATED line that marks a cut
     * text answer.
     */
    private static final int FAILED_SNAPSHOT_CHARS = McpToolOutput.MAX_CHARS / 2;

    private final OperationKind kind;
    private final BoundedJobs<String, V> jobs;
    private final McpOperationRegistry operations;
    private final OperationAnswers answers;

    public BoundedOperation(OperationKind kind, BoundedJobs<String, V> jobs, McpOperationRegistry operations,
            OperationAnswers answers) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.answers = Objects.requireNonNull(answers, "answers");
    }

    /** One per kind, waiting the standard budget and keeping outcomes for the standard hour. */
    public static <V> BoundedOperation<V> standard(
            OperationKind kind, McpOperationRegistry operations, OperationAnswers answers, Clock clock) {
        return new BoundedOperation<>(kind,
                new BoundedJobs<>(BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, clock,
                        Schedulers.sharedVirtual(), BoundedJobs.UNBOUNDED_CONCURRENCY, BoundedJobs.DEFAULT_MAX_RETAINED),
                operations, answers);
    }

    /**
     * Starts the work, or joins the run already going under its key, and answers with the tool's own
     * record: for a run that finished within the wait budget, and for one still going -- or with the
     * task to follow, for a client that declared tasks. Each record is built with the operation as
     * {@code operations_status} reports it, so the caller that waited, the one that polls and a task
     * following the operation all read one shape.
     *
     * @throws ToolExecutionException when the work failed, carrying the operation so it can still be read
     */
    public McpToolOutcome run(Work<V> work, McpCallContext call, Replies<V> replies) {
        Objects.requireNonNull(replies, "replies");
        OperationHandle<V> handle = jobs.startOrJoin(work.key(), true, value -> false, () -> holding(work));
        String operationId = handle.operationId();
        // The finished answer carries the value as its own payload, so the operation beside it is only
        // the attempt behind it: its result would repeat the payload. operations_status still has it.
        Function<V, McpToolResult> answer = value -> McpToolResult.of(
                replies.finished().apply(value, operations.status(operationId).withoutResult()));
        operations.register(kind, handle, work.present(), answer, work.retry());
        Optional<V> finished = awaited(handle, call);
        if (finished.isEmpty()) {
            return answers.stillRunning(call, operationId,
                    () -> McpToolResult.of(replies.running().apply(operations.status(operationId))));
        }
        return answer.apply(finished.get());
    }

    private Optional<V> awaited(OperationHandle<V> handle, McpCallContext call) {
        try {
            return jobs.awaitWithin(handle, answers.waitBudget(call, jobs.waitBudget()));
        } catch (RuntimeException failure) {
            throw new ToolExecutionException(FAILED.formatted(kind.name(), failure.getMessage(),
                    McpToolOutput.cappedWithin(Json.toString(operations.status(handle.operationId())),
                            FAILED_SNAPSHOT_CHARS)), failure);
        }
    }

    /**
     * Runs the work with the profile held open, since the call that started it closes its own hold
     * on return and the work may outlive it by minutes.
     */
    private static <V> V holding(Work<V> work) {
        AutoCloseable lease = work.lease().get();
        try {
            return work.compute().get();
        } finally {
            release(lease);
        }
    }

    /**
     * Reported rather than thrown: this runs in the {@code finally} after the work, where an
     * exception would replace the result or the failure worth reading.
     */
    private static void release(AutoCloseable lease) {
        try {
            lease.close();
        } catch (Exception e) {
            LOG.warn("Cannot release the bounded operation lease: message={}", e.getMessage(), e);
        }
    }

    /**
     * @param key     what makes two calls the same work, so the second joins the first
     * @param lease   acquired by the worker and held while it computes
     * @param compute the slow part, run off the caller's thread
     * @param present turns a result into what {@code operations_status} reports as the operation's
     *                result; it runs on whichever request first sees the result, so anything that needs
     *                the request it was asked in is read before, not in it
     * @param retry   the call that starts a new attempt with this one's arguments, which a failed or
     *                cancelled operation names in its {@code followUp}
     */
    public record Work<V>(
            String key,
            Supplier<? extends AutoCloseable> lease,
            Supplier<V> compute,
            Function<V, ? extends Record> present,
            McpNextTool retry) {

        public Work {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key is required");
            }
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(compute, "compute");
            Objects.requireNonNull(present, "present");
            Objects.requireNonNull(retry, "retry");
        }
    }

    /**
     * What a tool that answers with a record says about a run, each given the operation as
     * {@code operations_status} reports it at that moment.
     *
     * @param finished the answer to a run that produced its value, given the operation without its
     *                 {@code result}, which the answer carries itself
     * @param running  the answer to a run that outlasted the wait, for a caller that polls
     */
    public record Replies<V>(
            BiFunction<V, McpOperationRegistry.Snapshot, ? extends Record> finished,
            Function<McpOperationRegistry.Snapshot, ? extends Record> running) {

        public Replies {
            Objects.requireNonNull(finished, "finished");
            Objects.requireNonNull(running, "running");
        }
    }
}
