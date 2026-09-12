/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.hub.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Every recording session on every connected hub, flattened into one list a reader can choose from.
 * The scan advances in bounded stages so a slow project cannot discard rows already returned by
 * another project on the same hub.
 */
public final class HubSessionScan {

    private static final Logger LOG = LoggerFactory.getLogger(HubSessionScan.class);

    private static final String UNREACHABLE = "unreachable";
    private static final String DEADLINE_EXCEEDED = "deadline exceeded";
    private static final String CAPACITY_EXHAUSTED = "scan capacity exhausted";
    private static final int MAX_CONCURRENT_RPCS = 16;

    /**
     * At most {@link #MAX_CONCURRENT_RPCS} hub calls at once, and every call that does not fit waits
     * its turn rather than being refused.
     * <p>
     * The queue is deliberately unbounded. What this pool exists to limit is how many hub RPCs are in
     * flight together, and the core threads already do that; what limits the scan as a whole is its
     * deadline. A bounded queue adds a third limit that is not about either, and it is the one a large
     * installation hits first: the scan submits a whole level at once, so a workspace with more
     * projects than the queue holds would lose the overflow to {@code scan capacity exhausted} while
     * the deadline still had most of its budget left. Work refused for lack of room is indistinguishable
     * to the reader from a hub that would not answer, which is the one thing this class exists to keep
     * separate. The queued calls are small, and the deadline bounds how long they can accumulate.
     */
    private static final ThreadPoolExecutor RPC_EXECUTOR = new ThreadPoolExecutor(
            MAX_CONCURRENT_RPCS,
            MAX_CONCURRENT_RPCS,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            Thread.ofVirtual().name("hub-mcp-scan-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    private static final ScheduledExecutorService DEADLINE_SCHEDULER = deadlineScheduler();

    private final HubsManager hubsManager;
    private final Duration budget;

    public HubSessionScan(HubsManager hubsManager, Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive: budget=" + budget);
        }
        this.hubsManager = hubsManager;
        this.budget = budget;
    }

    /** Stable live-catalogue order, including sessions with equal creation times. */
    public record Key(Instant createdAt, HubSessionRef ref) implements Comparable<Key> {

        private static final Comparator<Key> ORDER = Comparator.comparing(
                        Key::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(key -> key.ref().hubId())
                .thenComparing(key -> key.ref().workspaceId())
                .thenComparing(key -> key.ref().projectId())
                .thenComparing(key -> key.ref().sessionId());

        @Override
        public int compareTo(Key other) {
            return ORDER.compare(this, other);
        }
    }

    public record Row(
            HubSessionRef ref,
            String hubName,
            String workspaceName,
            String projectName,
            RecordingSession session) {

        public Key key() {
            return new Key(session.createdAt(), ref);
        }
    }

    public record Failure(String hubName, String scope, String reason) {
    }

    public record Result(List<Row> rows, List<Failure> failures) {

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    private record HubWork(HubManager hub, HubInfo info, String name) {
    }

    private record WorkspaceWork(HubWork hub, WorkspaceInfo workspace) {
    }

    private record ProjectWork(WorkspaceWork workspace, ProjectManager project) {
    }

    private record ScopedCall<T>(String hubName, String scope, Callable<T> callable) {
    }

    private record TaskResult<T>(String hubName, String scope, T value, Exception error) {
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
    public Result scan(HubScanFilter filter) {
        return scan(filter.withSessions(filter.sessions().withLimit(RecordingSessionFilter.NO_LIMIT)), 0);
    }

    public Result scan(HubScanFilter filter, int limit) {
        List<HubManager> hubs = hubsManager.findAll().stream()
                .filter(hub -> filter.matches(hub.info()))
                .toList();
        if (hubs.isEmpty()) {
            return new Result(List.of(), List.of());
        }

        Deadline deadline = Deadline.after(budget.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, DEADLINE_SCHEDULER);
        try {
            return context.call(() -> scanWithin(hubs, filter, limit, context, deadline));
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Hub scan failed", e);
        } finally {
            context.cancel(null);
        }
    }

    /**
     * Probes all supplied hubs under the same bounded, cancellable RPC policy as a session scan.
     * Missing entries are the hubs that failed or crossed the deadline.
     */
    public Map<String, Optional<String>> probeVersions(List<HubManager> hubs) {
        Deadline deadline = Deadline.after(budget.toNanos(), TimeUnit.NANOSECONDS);
        Context.CancellableContext context = Context.current().withDeadline(deadline, DEADLINE_SCHEDULER);
        try {
            return context.call(() -> {
                StageResult<Map.Entry<String, Optional<String>>> probes = await(hubs.stream()
                        .map(hub -> {
                            HubInfo info = hub.info();
                            String name = hubName(info);
                            return new ScopedCall<Map.Entry<String, Optional<String>>>(name, name, () -> {
                                String version = hub.infoOrThrow().version();
                                return Map.entry(info.hubId(), Optional.of(version == null ? "" : version));
                            });
                        })
                        .toList(), context, deadline);
                Map<String, Optional<String>> versions = new HashMap<>();
                for (Map.Entry<String, Optional<String>> entry : probes.values()) {
                    versions.put(entry.getKey(), entry.getValue());
                }
                return versions;
            });
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Hub probe failed", e);
        } finally {
            context.cancel(null);
        }
    }

    private Result scanWithin(
            List<HubManager> hubs,
            HubScanFilter filter,
            int limit,
            Context context,
            Deadline deadline) {

        ExecutorCompletionService<TaskResult<ScanExpansion>> completion =
                new ExecutorCompletionService<>(RPC_EXECUTOR);
        Map<Future<TaskResult<ScanExpansion>>, ScopedCall<ScanExpansion>> pending = new HashMap<>();
        List<Failure> failures = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        for (HubManager hub : hubs) {
            submit(probeCall(hub, filter), context, completion, pending, failures);
        }

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
                    throw new IllegalStateException("Interrupted while scanning hubs", e);
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
                failures.add(new Failure(call.hubName(), call.scope(), deadlineReason()));
            }
            cancel(pending);
        }

        List<Row> ordered = rows.stream()
                .sorted(Comparator.comparing(Row::key))
                .toList();
        List<Row> capped = limit > 0 && ordered.size() > limit ? ordered.subList(0, limit) : ordered;
        return new Result(capped, List.copyOf(failures));
    }

    private static ScopedCall<ScanExpansion> probeCall(HubManager manager, HubScanFilter filter) {
        HubInfo info = manager.info();
        String name = hubName(info);
        return new ScopedCall<>(name, name, () -> {
            manager.infoOrThrow();
            HubWork hub = new HubWork(manager, info, name);
            return ScanExpansion.children(List.of(workspaceCall(hub, filter)));
        });
    }

    private static ScopedCall<ScanExpansion> workspaceCall(HubWork hub, HubScanFilter filter) {
        return new ScopedCall<>(hub.name(), hub.name(), () ->
                ScanExpansion.children(hub.hub().workspacesOrThrow().stream()
                        .filter(filter::matches)
                        .map(workspace -> projectCall(new WorkspaceWork(hub, workspace), filter))
                        .toList()));
    }

    private static ScopedCall<ScanExpansion> projectCall(WorkspaceWork workspace, HubScanFilter filter) {
        String scope = workspace.hub().name() + "/" + workspace.workspace().name();
        return new ScopedCall<>(workspace.hub().name(), scope, () -> {
            WorkspaceManager manager = workspace.hub().hub().workspace(workspace.workspace());
            return ScanExpansion.children(manager.projectsManager().findAllOrThrow().stream()
                    .filter(project -> filter.matches(project.info()))
                    .map(project -> sessionCall(new ProjectWork(workspace, project), filter))
                    .toList());
        });
    }

    private static ScopedCall<ScanExpansion> sessionCall(ProjectWork project, HubScanFilter filter) {
        HubWork hub = project.workspace().hub();
        WorkspaceInfo workspace = project.workspace().workspace();
        ProjectInfo projectInfo = project.project().info();
        String scope = hub.name() + "/" + workspace.name() + "/" + projectInfo.name();
        return new ScopedCall<>(hub.name(), scope,
                () -> ScanExpansion.rows(project.project().repositoryManager()
                        .listRecordingSessions(true, filter.sessions()).stream()
                        .map(session -> new Row(
                                new HubSessionRef(
                                        hub.info().hubId(), workspace.id(), projectInfo.id(), session.id()),
                                hub.name(), workspace.name(), projectInfo.name(), session))
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
            failures.add(new Failure(call.hubName(), call.scope(), CAPACITY_EXHAUSTED));
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
                failures.add(new Failure(result.hubName(), result.scope(), reasonOf(result.error())));
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
            throw new IllegalStateException("Interrupted while collecting a hub scan", e);
        } catch (ExecutionException e) {
            failures.add(new Failure(call.hubName(), call.scope(), reasonOf(e)));
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
                failures.add(new Failure(call.hubName(), call.scope(), CAPACITY_EXHAUSTED));
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
            LOG.warn("Hub scan stage did not finish within its deadline: pending={} budget={}",
                    pending.size(), budget);
            for (ScopedCall<T> call : pending.values()) {
                failures.add(new Failure(call.hubName(), call.scope(), deadlineReason()));
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
                failures.add(new Failure(result.hubName(), result.scope(), reasonOf(result.error())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancel(pending);
            throw new IllegalStateException("Interrupted while collecting a hub scan", e);
        } catch (ExecutionException e) {
            failures.add(new Failure(call.hubName(), call.scope(), reasonOf(e)));
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
            throw new IllegalStateException("Interrupted while scanning hubs", e);
        }
    }

    private static <T> TaskResult<T> execute(ScopedCall<T> call) {
        try {
            return new TaskResult<>(call.hubName(), call.scope(), call.callable().call(), null);
        } catch (Exception e) {
            LOG.warn("Failed to read a hub during an MCP scan: scope={} reason={}",
                    call.scope(), e.getMessage(), e);
            return new TaskResult<>(call.hubName(), call.scope(), null, e);
        }
    }

    private static void cancel(Map<? extends Future<?>, ?> pending) {
        for (Future<?> future : pending.keySet()) {
            future.cancel(true);
        }
    }

    private String deadlineReason() {
        if (budget.compareTo(Duration.ofSeconds(1)) < 0) {
            return DEADLINE_EXCEEDED + " after " + budget.toMillis() + "ms";
        }
        return DEADLINE_EXCEEDED + " after " + budget.toSeconds() + "s";
    }

    private static String reasonOf(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof StatusRuntimeException grpc) {
                Status.Code code = grpc.getStatus().getCode();
                if (code == Status.Code.UNAVAILABLE) {
                    return UNREACHABLE;
                }
                if (code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.CANCELLED) {
                    return DEADLINE_EXCEEDED;
                }
                return GrpcClientErrors.toJeffreyException(grpc).getMessage();
            }
            if (cause instanceof JeffreyException jeffrey) {
                return jeffrey.getCode() == ErrorCode.HUB_UNAVAILABLE
                        ? UNREACHABLE
                        : jeffrey.getMessage();
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    private static ScheduledExecutorService deadlineScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                Thread.ofPlatform().daemon().name("hub-mcp-deadline-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static String hubName(HubInfo info) {
        return info.name() == null || info.name().isBlank() ? info.hubId() : info.name();
    }
}
