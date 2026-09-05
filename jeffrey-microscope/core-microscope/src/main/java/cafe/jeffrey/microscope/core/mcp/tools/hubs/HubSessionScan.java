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
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Every recording session on every connected hub, flattened into one list a reader can choose from.
 * <p>
 * Two things make this more than a nested loop.
 * <p>
 * <strong>An unreachable hub has to be visible.</strong> {@link HubManager#workspaces()} and the
 * projects listing below it both log and return an empty list when a hub is down, which is right for
 * a UI and wrong here: a reader told "no sessions" when the truth is "production did not answer"
 * will conclude their recordings are missing. So each hub is probed with
 * {@link HubManager#tryInfo()} first, and one that does not answer is reported as a
 * {@link Failure} rather than silently contributing nothing.
 * <p>
 * <strong>The scan has to end.</strong> No deadline is configured on the hub channels, and this runs
 * inside a synchronous MCP tool call, so a hub whose connect blackholes rather than refusing would
 * otherwise hang the caller indefinitely. Work is dispatched onto virtual threads and awaited under
 * a budget. Note the honest limit of that: the budget bounds <em>this</em> caller, not the RPCs. The
 * abandoned threads finish on their own and are cheap, but they are not cancelled — the real fix is
 * a deadline on the {@code hub-client} stubs.
 */
public final class HubSessionScan {

    private static final Logger LOG = LoggerFactory.getLogger(HubSessionScan.class);

    private static final String UNREACHABLE = "unreachable";

    private final HubsManager hubsManager;
    private final Duration budget;

    public HubSessionScan(HubsManager hubsManager, Duration budget) {
        this.hubsManager = hubsManager;
        this.budget = budget;
    }

    /**
     * One session, with everything needed to name it to a reader and to fetch it again.
     */
    public record Row(
            HubSessionRef ref,
            String hubName,
            String workspaceName,
            String projectName,
            RecordingSession session) {
    }

    /**
     * Somewhere the scan could not see. Reported alongside whatever did come back, never thrown.
     *
     * @param scope what could not be read, as a reader would name it: a hub, or a project on one.
     *              Always specific enough to act on — "a hub is down" is not a useful thing to be
     *              told when three are configured
     */
    public record Failure(String hubName, String scope, String reason) {
    }

    public record Result(List<Row> rows, List<Failure> failures) {

        public boolean complete() {
            return failures.isEmpty();
        }
    }

    /**
     * Scans every hub the filter admits, newest session first.
     *
     * @param filter which hubs, workspaces and projects to look at, and what to ask each hub for
     * @param limit  the most rows to return overall, or {@code 0} for all of them
     */
    public Result scan(HubScanFilter filter, int limit) {
        List<HubManager> hubs = hubsManager.findAll().stream()
                .filter(hub -> filter.matches(hub.info()))
                .toList();

        if (hubs.isEmpty()) {
            return new Result(List.of(), List.of());
        }

        List<CompletableFuture<Result>> perHub = hubs.stream()
                .map(hub -> CompletableFuture.supplyAsync(
                        () -> scanHub(hub, filter), Schedulers.sharedVirtual()))
                .toList();

        Result merged = awaitWithinBudget(perHub, hubs);

        List<Row> ordered = merged.rows().stream()
                .sorted(Comparator.comparing(
                        (Row row) -> row.session().createdAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        // The hub applied the limit per project, so the merged list can still exceed it.
        List<Row> capped = limit > 0 && ordered.size() > limit ? ordered.subList(0, limit) : ordered;
        return new Result(capped, merged.failures());
    }

    /**
     * Collects whatever finished in time. A hub still running when the budget expires is reported as
     * having not answered, which is the same thing from the reader's point of view.
     */
    private Result awaitWithinBudget(List<CompletableFuture<Result>> perHub, List<HubManager> hubs) {
        try {
            CompletableFuture.allOf(perHub.toArray(new CompletableFuture[0]))
                    .get(budget.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("Hub scan did not finish within its budget: budget_in_sec={} hubs={}",
                    budget.toSeconds(), hubs.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while scanning hubs", e);
        } catch (Exception e) {
            // Individual hubs never fail their future; anything here is a defect worth surfacing.
            LOG.warn("Hub scan failed", e);
        }

        List<Row> rows = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < perHub.size(); i++) {
            CompletableFuture<Result> future = perHub.get(i);
            String hubName = hubName(hubs.get(i).info());
            if (!future.isDone() || future.isCompletedExceptionally()) {
                failures.add(new Failure(hubName, hubName,
                        "did not answer within " + budget.toSeconds() + "s"));
                continue;
            }
            Result result = future.join();
            rows.addAll(result.rows());
            failures.addAll(result.failures());
        }
        return new Result(rows, failures);
    }

    private Result scanHub(HubManager hub, HubScanFilter filter) {
        HubInfo info = hub.info();
        String hubName = hubName(info);

        if (hub.tryInfo().isEmpty()) {
            return new Result(List.of(), List.of(new Failure(hubName, hubName, UNREACHABLE)));
        }

        List<WorkspaceInfo> workspaces = hub.workspaces().stream()
                .filter(filter::matches)
                .toList();

        List<CompletableFuture<Result>> perWorkspace = workspaces.stream()
                .map(workspace -> CompletableFuture.supplyAsync(
                        () -> scanWorkspace(hub, info, hubName, workspace, filter),
                        Schedulers.sharedVirtual()))
                .toList();

        List<Row> rows = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (CompletableFuture<Result> future : perWorkspace) {
            Result result = future.join();
            rows.addAll(result.rows());
            failures.addAll(result.failures());
        }
        return new Result(rows, failures);
    }

    private Result scanWorkspace(
            HubManager hub,
            HubInfo info,
            String hubName,
            WorkspaceInfo workspace,
            HubScanFilter filter) {

        Optional<WorkspaceManager> workspaceManager = hub.workspace(workspace.id());
        if (workspaceManager.isEmpty()) {
            return new Result(List.of(), List.of());
        }

        List<ProjectManager> projects = workspaceManager.get().projectsManager().findAll().stream()
                .filter(project -> filter.matches(project.info()))
                .toList();

        List<Row> rows = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (ProjectManager project : projects) {
            ProjectInfo projectInfo = project.info();
            try {
                for (RecordingSession session
                        : project.repositoryManager().listRecordingSessions(true, filter.sessions())) {
                    rows.add(new Row(
                            new HubSessionRef(info.hubId(), workspace.id(), projectInfo.id(), session.id()),
                            hubName,
                            workspace.name(),
                            projectInfo.name(),
                            session));
                }
            } catch (Exception e) {
                LOG.warn("Failed to list sessions during a hub scan: hub={} project={}",
                        hubName, projectInfo.id(), e);
                failures.add(new Failure(hubName, hubName + "/" + projectInfo.name(), reasonOf(e)));
            }
        }
        return new Result(rows, failures);
    }

    /**
     * What to tell the reader went wrong. A hub that is down and a hub that refused a specific call
     * lead to different next steps, so they must not both read as "unreachable".
     */
    private static String reasonOf(Exception exception) {
        if (exception instanceof StatusRuntimeException grpc) {
            JeffreyException mapped = GrpcClientErrors.toJeffreyException(grpc);
            return mapped.getCode() == ErrorCode.REMOTE_JEFFREY_UNAVAILABLE
                    ? UNREACHABLE
                    : mapped.getMessage();
        }
        if (exception instanceof JeffreyException jeffrey) {
            return jeffrey.getMessage();
        }
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    private static String hubName(HubInfo info) {
        return info.name() == null || info.name().isBlank() ? info.hubId() : info.name();
    }
}
