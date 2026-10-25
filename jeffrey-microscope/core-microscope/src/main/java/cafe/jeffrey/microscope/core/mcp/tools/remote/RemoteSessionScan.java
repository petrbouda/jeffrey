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

package cafe.jeffrey.microscope.core.mcp.tools.remote;

import cafe.jeffrey.microscope.remote.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.tools.McpDeadlines;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Every recording session on the remote, flattened into one list a reader can choose from. The scan
 * advances in bounded stages so a slow project cannot discard rows already returned by another
 * project.
 */
public final class RemoteSessionScan {

    private static final String SCAN_FAILED = "Remote scan failed: ";
    private static final String PROBE_FAILED = "Remote probe failed: ";
    private static final String REMOTE_SCOPE = "remote";
    private static final Logger LOG = LoggerFactory.getLogger(RemoteSessionScan.class);

    private static final String UNREACHABLE = "unreachable";
    private static final String DEADLINE_EXCEEDED = "deadline exceeded";
    private static final String CAPACITY_EXHAUSTED = "scan capacity exhausted";
    private static final int MAX_CONCURRENT_RPCS = 16;

    /**
     * At most {@link #MAX_CONCURRENT_RPCS} remote calls at once, and every call that does not fit waits
     * its turn rather than being refused.
     * <p>
     * The queue is deliberately unbounded. What this pool exists to limit is how many remote calls are in
     * flight together, and the core threads already do that; what limits the scan as a whole is its
     * deadline. A bounded queue adds a third limit that is not about either, and it is the one a large
     * installation hits first: the scan submits a whole level at once, so a workspace with more
     * projects than the queue holds would lose the overflow to {@code scan capacity exhausted} while
     * the deadline still had most of its budget left. Work refused for lack of room is indistinguishable
     * to the reader from a remote that would not answer, which is the one thing this class exists to keep
     * separate. The queued calls are small, and the deadline bounds how long they can accumulate.
     */
    private static final ThreadPoolExecutor RPC_EXECUTOR = new ThreadPoolExecutor(
            MAX_CONCURRENT_RPCS,
            MAX_CONCURRENT_RPCS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            Thread.ofVirtual().name("remote-mcp-scan-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    private final RemoteManager remoteManager;
    private final Duration budget;

    public RemoteSessionScan(RemoteManager remoteManager, Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive: budget=" + budget);
        }
        this.remoteManager = remoteManager;
        this.budget = budget;
    }

    /** Stable live-catalogue order, including sessions with equal creation times. */
    public record Key(Instant createdAt, RemoteSessionRef ref) implements Comparable<Key> {

        private static final Comparator<Key> ORDER = Comparator.comparing(
                        Key::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(key -> key.ref().workspaceId())
                .thenComparing(key -> key.ref().projectId())
                .thenComparing(key -> key.ref().sessionId());

        @Override
        public int compareTo(Key other) {
            return ORDER.compare(this, other);
        }
    }

    public record Row(
            RemoteSessionRef ref,
            String workspaceName,
            String projectName,
            RecordingSession session) {

        public Key key() {
            return new Key(session.createdAt(), ref);
        }
    }

    /**
     * @param kind why the scope could not be read, as a category rather than a sentence, so that
     *             whatever counts failures — the diagnostics resource — counts on the classification
     *             this scan made rather than on the wording of {@code reason}
     */
    public record Failure(String scope, Kind kind, String reason) {

        /** The categories a scan sorts its failures into; {@link #OTHER} carries the remote's own message. */
        public enum Kind {
            UNREACHABLE, DEADLINE_EXCEEDED, CAPACITY_EXHAUSTED, OTHER
        }
    }

    public record Result(List<Row> rows, List<Failure> failures) {

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    private record WorkspaceWork(RemoteConnection remote, WorkspaceInfo workspace) {
    }

    private record ProjectWork(WorkspaceWork workspace, ProjectManager project) {
    }

    private record ScopedCall<T>(String scope, Callable<T> callable) {
    }

    private record TaskResult<T>(String scope, T value, Exception error) {
    }

    private record StageResult<T>(List<T> values, List<Failure> failures) {
    }

    private record ScanExpansion(List<ScopedCall<ScanExpansion>> children, List<Row> rows) {

        private static ScanExpansion children(List<ScopedCall<ScanExpansion>> children) {
            return new ScanExpansion(children, List.of());
        }

        private static ScanExpansion rows(List<Row> rows) {
            return new ScanExpansion(List.of(), rows);
        }
    }

    /** Reads every matching project row so catalogue pagination can report an honest total. */
    public Result scan(RemoteScanFilter filter) {
        return scan(filter.withSessions(filter.sessions().withLimit(RecordingSessionFilter.NO_LIMIT)), 0);
    }

    public Result scan(RemoteScanFilter filter, int limit) {
        Optional<RemoteConnection> remote = remoteManager.current();
        if (remote.isEmpty()) {
            return new Result(List.of(), List.of());
        }

        Deadline deadline = McpDeadlines.after(budget);
        Context.CancellableContext context = McpDeadlines.withDeadline(Context.current(), deadline);
        try {
            return context.call(() -> scanWithin(remote.get(), filter, limit, context, deadline));
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolExecutionException(SCAN_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    /**
     * Whether the remote answers and what it is running, under the same bounded, cancellable call
     * policy as a session scan. The version is empty when the probe failed or crossed the deadline,
     * and the failure says which.
     */
    public record ProbeResult(Optional<String> version, List<Failure> failures) {

        public boolean reachable() {
            return version.isPresent();
        }
    }

    public ProbeResult probe(RemoteConnection remote) {
        Deadline deadline = McpDeadlines.after(budget);
        Context.CancellableContext context = McpDeadlines.withDeadline(Context.current(), deadline);
        try {
            return context.call(() -> {
                StageResult<String> probes = await(List.of(new ScopedCall<String>(REMOTE_SCOPE, () -> {
                    String version = remote.infoOrThrow().version();
                    return version == null ? "" : version;
                })), context, deadline);
                return new ProbeResult(probes.values().stream().findFirst(), probes.failures());
            });
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolExecutionException(PROBE_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    private Result scanWithin(
            RemoteConnection remote,
            RemoteScanFilter filter,
            int limit,
            Context context,
            Deadline deadline) {

        ExecutorCompletionService<TaskResult<ScanExpansion>> completion =
                new ExecutorCompletionService<>(RPC_EXECUTOR);
        Map<Future<TaskResult<ScanExpansion>>, ScopedCall<ScanExpansion>> pending = new HashMap<>();
        List<Failure> failures = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        submit(probeCall(remote, filter), context, completion, pending, failures);

        while (!pending.isEmpty()) {
            Future<TaskResult<ScanExpansion>> completed = completion.poll();
            if (completed == null) {
                long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
                if (remaining <= 0) {
                    break;
                }
                try {
                    completed = completion.poll(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancel(pending);
                    throw new IllegalStateException("Interrupted while scanning the remote", e);
                }
                if (completed == null) {
                    continue;
                }
            }
            collect(completed, context, completion, pending, failures, rows);
        }

        // A deadline can expire between the non-blocking poll and the remaining-time check. Drain
        // completions once more so a row already returned by a fast project is never mislabeled lost.
        Future<TaskResult<ScanExpansion>> completed;
        while ((completed = completion.poll()) != null) {
            collect(completed, context, completion, pending, failures, rows);
        }

        if (!pending.isEmpty()) {
            for (ScopedCall<ScanExpansion> call : pending.values()) {
                failures.add(deadlineFailure(call.scope()));
            }
            cancel(pending);
        }

        List<Row> ordered = rows.stream()
                .sorted(Comparator.comparing(Row::key))
                .toList();
        List<Row> capped = limit > 0 && ordered.size() > limit ? ordered.subList(0, limit) : ordered;
        return new Result(capped, List.copyOf(failures));
    }

    private static ScopedCall<ScanExpansion> probeCall(RemoteConnection remote, RemoteScanFilter filter) {
        return new ScopedCall<>(REMOTE_SCOPE, () -> {
            remote.infoOrThrow();
            return ScanExpansion.children(List.of(workspaceCall(remote, filter)));
        });
    }

    private static ScopedCall<ScanExpansion> workspaceCall(RemoteConnection remote, RemoteScanFilter filter) {
        return new ScopedCall<>(REMOTE_SCOPE, () ->
                ScanExpansion.children(remote.workspacesOrThrow().stream()
                        .filter(filter::matches)
                        .map(workspace -> projectCall(new WorkspaceWork(remote, workspace), filter))
                        .toList()));
    }

    private static ScopedCall<ScanExpansion> projectCall(WorkspaceWork workspace, RemoteScanFilter filter) {
        String scope = workspace.workspace().name();
        return new ScopedCall<>(scope, () -> {
            WorkspaceManager manager = workspace.remote().workspace(workspace.workspace());
            return ScanExpansion.children(manager.projectsManager().findAllOrThrow().stream()
                    .filter(project -> filter.matches(project.info()))
                    .map(project -> sessionCall(new ProjectWork(workspace, project), filter))
                    .toList());
        });
    }

    private static ScopedCall<ScanExpansion> sessionCall(ProjectWork project, RemoteScanFilter filter) {
        WorkspaceInfo workspace = project.workspace().workspace();
        ProjectInfo projectInfo = project.project().info();
        String scope = workspace.name() + "/" + projectInfo.name();
        return new ScopedCall<>(scope,
                () -> ScanExpansion.rows(project.project().repositoryManager()
                        .listRecordingSessions(filter.sessions()).stream()
                        .map(session -> new Row(
                                new RemoteSessionRef(workspace.id(), projectInfo.id(), session.id()),
                                workspace.name(), projectInfo.name(), session))
                        .toList()));
    }

    private static void submit(
            ScopedCall<ScanExpansion> call,
            Context context,
            ExecutorCompletionService<TaskResult<ScanExpansion>> completion,
            Map<Future<TaskResult<ScanExpansion>>, ScopedCall<ScanExpansion>> pending,
            List<Failure> failures) {

        try {
            Future<TaskResult<ScanExpansion>> future = completion.submit(context.wrap(() -> execute(call)));
            pending.put(future, call);
        } catch (RejectedExecutionException e) {
            failures.add(capacityFailure(call.scope()));
        }
    }

    private static void collect(
            Future<TaskResult<ScanExpansion>> completed,
            Context context,
            ExecutorCompletionService<TaskResult<ScanExpansion>> completion,
            Map<Future<TaskResult<ScanExpansion>>, ScopedCall<ScanExpansion>> pending,
            List<Failure> failures,
            List<Row> rows) {

        ScopedCall<ScanExpansion> call = pending.remove(completed);
        if (call == null) {
            return;
        }
        try {
            TaskResult<ScanExpansion> result = completed.get();
            if (result.error() != null) {
                failures.add(failureOf(result.scope(), result.error()));
                return;
            }
            ScanExpansion expansion = result.value();
            rows.addAll(expansion.rows());
            for (ScopedCall<ScanExpansion> child : expansion.children()) {
                submit(child, context, completion, pending, failures);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(pending);
            throw new IllegalStateException("Interrupted while collecting a remote scan", e);
        } catch (ExecutionException e) {
            failures.add(failureOf(call.scope(), e));
        }
    }

    private <T> StageResult<T> await(
            List<ScopedCall<T>> calls,
            Context context,
            Deadline deadline) {

        if (calls.isEmpty()) {
            return new StageResult<>(List.of(), List.of());
        }

        ExecutorCompletionService<TaskResult<T>> completion = new ExecutorCompletionService<>(RPC_EXECUTOR);
        Map<Future<TaskResult<T>>, ScopedCall<T>> pending = new HashMap<>();
        List<Failure> failures = new ArrayList<>();
        for (ScopedCall<T> call : calls) {
            try {
                Future<TaskResult<T>> future = completion.submit(context.wrap(() -> execute(call)));
                pending.put(future, call);
            } catch (RejectedExecutionException e) {
                failures.add(capacityFailure(call.scope()));
            }
        }

        List<T> values = new ArrayList<>();
        while (!pending.isEmpty()) {
            Future<TaskResult<T>> completed = completion.poll();
            if (completed == null) {
                completed = poll(completion, pending, deadline);
            }
            if (completed == null) {
                break;
            }
            collectStage(completed, pending, failures, values);
        }

        Future<TaskResult<T>> completed;
        while ((completed = completion.poll()) != null) {
            collectStage(completed, pending, failures, values);
        }

        if (!pending.isEmpty()) {
            LOG.warn("Remote scan stage did not finish within its deadline: pending={} budget={}",
                    pending.size(), budget);
            for (ScopedCall<T> call : pending.values()) {
                failures.add(deadlineFailure(call.scope()));
            }
            cancel(pending);
        }
        return new StageResult<>(List.copyOf(values), List.copyOf(failures));
    }

    private static <T> void collectStage(
            Future<TaskResult<T>> completed,
            Map<Future<TaskResult<T>>, ScopedCall<T>> pending,
            List<Failure> failures,
            List<T> values) {

        ScopedCall<T> call = pending.remove(completed);
        if (call == null) {
            return;
        }
        try {
            TaskResult<T> result = completed.get();
            if (result.error() == null) {
                values.add(result.value());
            } else {
                failures.add(failureOf(result.scope(), result.error()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(pending);
            throw new IllegalStateException("Interrupted while collecting a remote scan", e);
        } catch (ExecutionException e) {
            failures.add(failureOf(call.scope(), e));
        }
    }

    private static <T> Future<TaskResult<T>> poll(
            ExecutorCompletionService<TaskResult<T>> completion,
            Map<Future<TaskResult<T>>, ScopedCall<T>> pending,
            Deadline deadline) {

        long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
        if (remaining <= 0) {
            return null;
        }
        try {
            return completion.poll(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(pending);
            throw new IllegalStateException("Interrupted while scanning the remote", e);
        }
    }

    private static <T> TaskResult<T> execute(ScopedCall<T> call) {
        try {
            return new TaskResult<>(call.scope(), call.callable().call(), null);
        } catch (Exception e) {
            LOG.warn("Failed to read the remote during an MCP scan: scope={} reason={}",
                    call.scope(), e.getMessage(), e);
            return new TaskResult<>(call.scope(), null, e);
        }
    }

    private static void cancel(Map<? extends Future<?>, ?> pending) {
        for (Future<?> future : pending.keySet()) {
            future.cancel(true);
        }
    }

    private Failure deadlineFailure(String scope) {
        return new Failure(scope, Failure.Kind.DEADLINE_EXCEEDED, deadlineReason());
    }

    private String deadlineReason() {
        if (budget.compareTo(Duration.ofSeconds(1)) < 0) {
            return DEADLINE_EXCEEDED + " after " + budget.toMillis() + "ms";
        }
        return DEADLINE_EXCEEDED + " after " + budget.toSeconds() + "s";
    }

    private static Failure capacityFailure(String scope) {
        return new Failure(scope, Failure.Kind.CAPACITY_EXHAUSTED, CAPACITY_EXHAUSTED);
    }

    /** Classifies what a remote call threw, walking the cause chain for the gRPC status or Jeffrey code underneath. */
    private static Failure failureOf(String scope, Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof StatusRuntimeException grpc) {
                Status.Code code = grpc.getStatus().getCode();
                if (code == Status.Code.UNAVAILABLE) {
                    return new Failure(scope, Failure.Kind.UNREACHABLE, UNREACHABLE);
                }
                if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED) {
                    return new Failure(scope, Failure.Kind.DEADLINE_EXCEEDED, DEADLINE_EXCEEDED);
                }
                return new Failure(scope, Failure.Kind.OTHER,
                        GrpcClientErrors.toJeffreyException(grpc).getMessage());
            }
            if (cause instanceof JeffreyException jeffrey) {
                if (jeffrey.getCode() == ErrorCode.REMOTE_UNAVAILABLE) {
                    return new Failure(scope, Failure.Kind.UNREACHABLE, UNREACHABLE);
                }
                return new Failure(scope, Failure.Kind.OTHER, jeffrey.getMessage());
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        String message = exception.getMessage();
        return new Failure(scope, Failure.Kind.OTHER,
                message == null ? exception.getClass().getSimpleName() : message);
    }
}
