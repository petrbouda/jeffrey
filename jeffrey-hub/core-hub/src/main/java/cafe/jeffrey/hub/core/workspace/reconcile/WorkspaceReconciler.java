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

package cafe.jeffrey.hub.core.workspace.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.jfr.JfrMessageEmitter;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.core.streaming.SessionPaths;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.CreateProject;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Materializes the on-disk workspace declarations into the hub database. The provisioner
 * declares every project, instance and session by writing a marker file into its directory
 * ({@code .project-info.json}, {@code .instance-info.json}, {@code .session-info.json});
 * this reconciler scans those declarations and creates whatever the database does not know
 * yet — there is no event transport in between.
 *
 * <p><b>Strictly additive.</b> The absence of a directory or marker is never a delete
 * signal: removing old projects or sessions from the workspace volume has zero effect on
 * hub state. The only deleters of hub state are the hub's own retention jobs and
 * user-initiated deletes — and those also remove the on-disk declaration, precisely so
 * this reconciler does not re-create what the hub removed.</p>
 *
 * <p>Each materialization runs in its own transaction, so a crash mid-scan leaves nothing
 * half-created; the next scan converges. Idempotency comes
 * from natural keys (origin project id, instance id, session id), never from dedup. A
 * half-written marker fails JSON parsing, skips the subtree with a WARN and is retried on
 * the next tick.</p>
 */
public class WorkspaceReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceReconciler.class);

    private final Clock clock;
    private final HubJeffreyDirs jeffreyDirs;
    private final HubPlatformRepositories platformRepositories;
    private final SessionFinisher sessionFinisher;
    private final TransactionOperations transactionOperations;

    public WorkspaceReconciler(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            TransactionOperations transactionOperations) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.sessionFinisher = sessionFinisher;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Reconciles every project directory of one workspace. Used by the startup bootstrap, where
     * nothing has announced what changed and the whole tree has to be examined.
     */
    public int reconcile(ProjectsManager projectsManager, Path workspaceDir) {
        int materialized = 0;
        for (Path projectDir : childDirectories(workspaceDir)) {
            materialized += reconcileProjectDirectory(projectsManager, projectDir).materialized();
        }
        return materialized;
    }

    /**
     * Reconciles a single project directory.
     *
     * <p>{@link Result#declarationSeen()} reports whether the directory actually held a readable
     * {@code .project-info.json}. A caller driven by the pending index uses it to decide whether
     * the entry may be removed: a hint whose declaration is not readable yet — the provisioner
     * writes the marker first, but a reader can still arrive mid-write — must be kept and
     * retried rather than consumed.</p>
     */
    public Result reconcileProjectDirectory(ProjectsManager projectsManager, Path projectDir) {
        Optional<RemoteProject> projectMarker =
                readMarker(projectDir, JeffreyLayout.PROJECT_INFO_FILE, RemoteProject.class);
        if (projectMarker.isEmpty()) {
            return Result.NOTHING_DECLARED;
        }

        try {
            return new Result(reconcileProject(projectsManager, projectDir, projectMarker.get()), true);
        } catch (Exception e) {
            // One broken project must not stop the rest of the workspace; this subtree
            // is retried on the next scan
            LOG.warn("Failed to reconcile project directory, skipping subtree: project_dir={}", projectDir, e);
            return Result.NOTHING_DECLARED;
        }
    }

    /**
     * Outcome of reconciling one project directory.
     *
     * @param materialized    number of entities created (project + instances + sessions)
     * @param declarationSeen whether the project declared itself with a readable marker and its
     *                        subtree was processed without error
     */
    public record Result(int materialized, boolean declarationSeen) {

        static final Result NOTHING_DECLARED = new Result(0, false);
    }

    private int reconcileProject(ProjectsManager projectsManager, Path projectDir, RemoteProject marker) {

        int materialized = 0;
        Optional<ProjectManager> existing = projectsManager.findByOriginProjectId(marker.projectId());

        ProjectManager projectManager;
        if (existing.isPresent()) {
            projectManager = existing.get();
        } else {
            projectManager = transactionOperations.execute(_ ->
                    materializeProject(projectsManager, marker));
            materialized++;
        }

        // The repository row can be missing when a project row was created without one
        // (interrupted earlier run) — repair it independently of project creation
        if (projectManager.repositoryManager().info().isEmpty()) {
            transactionOperations.executeWithoutResult(_ -> materializeRepository(projectManager, marker));
        }

        KnownEntities known = readKnownEntities(projectManager);

        for (Path instanceDir : childDirectories(projectDir)) {
            materialized += reconcileInstance(projectManager, instanceDir, known);
        }
        return materialized;
    }

    /**
     * Reads everything the database already knows about this project in two queries, so the
     * per-instance lookups drop out of the scan entirely: cost stays flat in the number of
     * instances and sessions, which is what makes a short scan period affordable.
     *
     * <p>These sets are a purely <em>negative</em> index — "definitely already known". They are
     * never used to decide that something exists that the database has not confirmed; the
     * natural-key guards on the insert paths remain the actual safety net.</p>
     */
    private KnownEntities readKnownEntities(ProjectManager projectManager) {
        Set<String> instanceIds = projectManager.projectInstanceRepository().findAll().stream()
                .map(ProjectInstanceInfo::id)
                .collect(Collectors.toSet());

        // Resolved through project_instances, exactly like the per-instance query it replaces —
        // not through the repositories table, so a project whose repository row is missing still
        // reports its sessions instead of silently re-materializing all of them
        Map<String, Set<String>> sessionIdsByInstance =
                platformRepositories.findSessionsByProjectId(projectManager.info().id()).stream()
                        .collect(Collectors.groupingBy(
                                ProjectInstanceSessionInfo::instanceId,
                                Collectors.mapping(ProjectInstanceSessionInfo::sessionId, Collectors.toSet())));

        return new KnownEntities(instanceIds, sessionIdsByInstance);
    }

    private ProjectManager materializeProject(ProjectsManager projectsManager, RemoteProject marker) {

        CreateProject createProject = new CreateProject(
                marker.projectId(),
                marker.projectName(),
                marker.projectLabel(),
                null, // namespace — not declared on disk
                Instant.ofEpochMilli(marker.createdAt()),
                marker.attributes());

        ProjectManager projectManager = projectsManager.create(createProject);

        LOG.info("Project materialized from workspace directory: project_id={} origin_project_id={} name={}",
                projectManager.info().id(), marker.projectId(), marker.projectName());
        JfrMessageEmitter.projectCreated(marker.projectName(), projectManager.info().id());
        return projectManager;
    }

    private void materializeRepository(ProjectManager projectManager, RemoteProject marker) {
        RepositoryInfo projectRepository = new RepositoryInfo(
                null,
                marker.repositoryType(),
                marker.workspacesPath(),
                marker.relativeWorkspacePath(),
                marker.relativeProjectPath());

        projectManager.repositoryManager().create(projectRepository);
        LOG.info("Repository created for project: project_id={}", projectManager.info().id());
    }

    /**
     * The instance directory is named by its instance id, so an already-known instance is
     * recognized from the directory name alone — its marker file is never opened.
     */
    private int reconcileInstance(ProjectManager projectManager, Path instanceDir, KnownEntities known) {

        int materialized = 0;
        String instanceId = instanceDir.getFileName().toString();

        if (!known.instanceIds().contains(instanceId)) {
            Optional<RemoteProjectInstance> instanceMarker =
                    readMarker(instanceDir, JeffreyLayout.INSTANCE_INFO_FILE, RemoteProjectInstance.class);
            if (instanceMarker.isEmpty()) {
                return 0;
            }

            RemoteProjectInstance marker = instanceMarker.get();
            // The marker is authoritative for the id; the directory name only pre-filters, so a
            // tree whose directory names drifted from the ids cannot re-create the same instance
            // on every scan
            if (!known.instanceIds().contains(marker.instanceId())) {
                transactionOperations.executeWithoutResult(_ ->
                        materializeInstance(projectManager, marker));
                materialized++;
            }
            instanceId = marker.instanceId();
        }

        materialized += reconcileSessions(projectManager, instanceDir, instanceId, known);
        return materialized;
    }

    private void materializeInstance(ProjectManager projectManager, RemoteProjectInstance marker) {

        ProjectInstanceInfo instanceInfo = new ProjectInstanceInfo(
                marker.instanceId(),
                projectManager.info().id(),
                marker.instanceId(),
                ProjectInstanceStatus.PENDING,
                Instant.ofEpochMilli(marker.createdAt()),
                null, // finishedAt
                null, // expiringAt
                null, // expiredAt
                0,    // sessionCount — calculated dynamically
                null); // activeSessionId — calculated dynamically

        projectManager.projectInstanceRepository().insert(instanceInfo);

        LOG.info("Instance materialized from workspace directory: instance_id={} project_id={}",
                marker.instanceId(), projectManager.info().id());
        JfrMessageEmitter.instanceCreated(marker.instanceId(), projectManager.info().name(), projectManager.info().id());
    }

    private int reconcileSessions(
            ProjectManager projectManager, Path instanceDir, String instanceId, KnownEntities known) {

        Set<String> knownSessionIds = known.sessionIdsByInstance().getOrDefault(instanceId, Set.of());

        // Oldest-first so force-finishing prior unfinished sessions sees the same order the
        // sessions were originally created in
        List<RemoteProjectInstanceSession> newSessions = childDirectories(instanceDir).stream()
                .filter(sessionDir -> !knownSessionIds.contains(sessionDir.getFileName().toString()))
                .map(sessionDir -> readMarker(
                        sessionDir, JeffreyLayout.SESSION_INFO_FILE, RemoteProjectInstanceSession.class))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(RemoteProjectInstanceSession::createdAt))
                .toList();

        for (RemoteProjectInstanceSession session : newSessions) {
            transactionOperations.executeWithoutResult(_ ->
                    materializeSession(projectManager, session));
        }
        return newSessions.size();
    }

    private void materializeSession(ProjectManager projectManager, RemoteProjectInstanceSession marker) {

        ProjectInfo projectInfo = projectManager.info();
        Optional<RepositoryInfo> repositoryInfo = projectManager.repositoryManager().info();
        if (repositoryInfo.isEmpty()) {
            LOG.warn("Cannot materialize session, project repository not found: session_id={} project_id={}",
                    marker.sessionId(), projectInfo.id());
            return;
        }

        Instant originCreatedAt = Instant.ofEpochMilli(marker.createdAt());
        ProjectRepositoryRepository repositoryRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());

        int closedCount = closeUnfinishedSessions(
                repositoryRepository, projectInfo, repositoryInfo.get(), marker.instanceId(), originCreatedAt);
        if (closedCount > 0) {
            LOG.info("Auto-closed unfinished sessions for instance before creating new session: "
                            + "project_id={} instance_id={} closed={}",
                    projectInfo.id(), marker.instanceId(), closedCount);
        }

        ProjectInstanceSessionInfo sessionInfo = ProjectInstanceSessionInfo.notRetained(
                marker.sessionId(),
                repositoryInfo.get().id(),
                marker.instanceId(),
                marker.order(),
                Path.of(marker.relativeSessionPath()),
                originCreatedAt,
                clock.instant(),
                null);

        projectManager.repositoryManager().createSession(sessionInfo);

        // Transition instance to ACTIVE (handles PENDING→ACTIVE, FINISHED→ACTIVE, EXPIRED→ACTIVE)
        projectManager.projectInstanceRepository().updateStatus(marker.instanceId(), ProjectInstanceStatus.ACTIVE);

        LOG.info("Session materialized from workspace directory: project_id={} instance_id={} session_id={}",
                projectInfo.id(), marker.instanceId(), marker.sessionId());
        JfrMessageEmitter.sessionCreated(marker.sessionId(), marker.instanceId(), marker.order(), projectInfo.id());
    }

    /**
     * Closes unfinished sessions for an instance by delegating to {@link SessionFinisher#forceFinish}.
     * Sessions are processed in chronological order so that each session's fallback finished_at
     * is the originCreatedAt of the next session in the sequence, not the new session's timestamp.
     */
    private int closeUnfinishedSessions(
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            RepositoryInfo repositoryInfo,
            String instanceId,
            Instant newSessionCreatedAt) {

        List<ProjectInstanceSessionInfo> unfinished =
                repositoryRepository.findUnfinishedSessionsByInstanceId(instanceId).stream()
                        .sorted(Comparator.comparing(ProjectInstanceSessionInfo::originCreatedAt))
                        .toList();

        for (int i = 0; i < unfinished.size(); i++) {
            ProjectInstanceSessionInfo session = unfinished.get(i);
            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, session);

            // Use the next session's originCreatedAt as fallback, or the new session's createdAt for the last one
            Instant fallback = (i + 1 < unfinished.size())
                    ? unfinished.get(i + 1).originCreatedAt()
                    : newSessionCreatedAt;

            sessionFinisher.forceFinish(repositoryRepository, projectInfo, session, sessionPath, fallback);
        }

        return unfinished.size();
    }

    private static <T> Optional<T> readMarker(Path directory, String markerFileName, Class<T> type) {
        Path markerFile = directory.resolve(markerFileName);
        if (!Files.isRegularFile(markerFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.read(Files.readString(markerFile), type));
        } catch (Exception e) {
            // Possibly a partially-written marker — skip the subtree, the next scan retries
            LOG.warn("Skipping unreadable marker file (may be partially written): file={}", markerFile);
            return Optional.empty();
        }
    }

    /**
     * What the database already knows about one project, read once per project per scan.
     *
     * @param instanceIds          ids of every instance row of the project
     * @param sessionIdsByInstance session ids of the project, grouped by instance id
     */
    private record KnownEntities(
            Set<String> instanceIds,
            Map<String, Set<String>> sessionIdsByInstance) {
    }

    public static List<Path> childDirectories(Path parent) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(parent)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOG.warn("Cannot list directory: path={}", parent, e);
            return List.of();
        }
    }
}
