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
import cafe.jeffrey.hub.core.workspace.WorkspaceEventConverter;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
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
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
 * <p>Each materialization runs in its own transaction together with its audit event, so a
 * crash mid-scan leaves nothing half-created; the next scan converges. Idempotency comes
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
    private final WorkspaceEventPublisher workspaceEventPublisher;
    private final TransactionOperations transactionOperations;

    public WorkspaceReconciler(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            WorkspaceEventPublisher workspaceEventPublisher,
            TransactionOperations transactionOperations) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.sessionFinisher = sessionFinisher;
        this.workspaceEventPublisher = workspaceEventPublisher;
        this.transactionOperations = transactionOperations;
    }

    /**
     * Reconciles one workspace directory into the given workspace. Returns the number of
     * entities materialized (projects + instances + sessions).
     */
    public int reconcile(WorkspaceInfo workspaceInfo, ProjectsManager projectsManager, Path workspaceDir) {
        int materialized = 0;
        for (Path projectDir : childDirectories(workspaceDir)) {
            Optional<RemoteProject> projectMarker =
                    readMarker(projectDir, JeffreyLayout.PROJECT_INFO_FILE, RemoteProject.class);
            if (projectMarker.isEmpty()) {
                continue;
            }

            try {
                materialized += reconcileProject(workspaceInfo, projectsManager, projectDir, projectMarker.get());
            } catch (Exception e) {
                // One broken project must not stop the rest of the workspace; this subtree
                // is retried on the next scan
                LOG.warn("Failed to reconcile project directory, skipping subtree: project_dir={}", projectDir, e);
            }
        }
        return materialized;
    }

    private int reconcileProject(
            WorkspaceInfo workspaceInfo, ProjectsManager projectsManager, Path projectDir, RemoteProject marker) {

        int materialized = 0;
        Optional<ProjectManager> existing = projectsManager.findByOriginProjectId(marker.projectId());

        ProjectManager projectManager;
        if (existing.isPresent()) {
            projectManager = existing.get();
        } else {
            projectManager = transactionOperations.execute(_ ->
                    materializeProject(workspaceInfo, projectsManager, marker));
            materialized++;
        }

        // The repository row can be missing when a project row was created without one
        // (interrupted earlier run) — repair it independently of project creation
        if (projectManager.repositoryManager().info().isEmpty()) {
            transactionOperations.executeWithoutResult(_ -> materializeRepository(projectManager, marker));
        }

        for (Path instanceDir : childDirectories(projectDir)) {
            Optional<RemoteProjectInstance> instanceMarker =
                    readMarker(instanceDir, JeffreyLayout.INSTANCE_INFO_FILE, RemoteProjectInstance.class);
            if (instanceMarker.isEmpty()) {
                continue;
            }
            materialized += reconcileInstance(workspaceInfo, projectManager, instanceDir, instanceMarker.get());
        }
        return materialized;
    }

    private ProjectManager materializeProject(
            WorkspaceInfo workspaceInfo, ProjectsManager projectsManager, RemoteProject marker) {

        CreateProject createProject = new CreateProject(
                marker.projectId(),
                marker.projectName(),
                marker.projectLabel(),
                null, // namespace — not declared on disk
                Instant.ofEpochMilli(marker.createdAt()),
                marker.attributes());

        ProjectManager projectManager = projectsManager.create(createProject);
        publishAudit(workspaceInfo, WorkspaceEventConverter.projectCreated(
                clock.instant(), marker, workspaceInfo, WorkspaceEventCreator.WORKSPACE_RECONCILER_JOB));

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

    private int reconcileInstance(
            WorkspaceInfo workspaceInfo, ProjectManager projectManager, Path instanceDir, RemoteProjectInstance marker) {

        int materialized = 0;
        String instanceId = marker.instanceId();

        if (projectManager.projectInstanceRepository().find(instanceId).isEmpty()) {
            transactionOperations.executeWithoutResult(_ ->
                    materializeInstance(workspaceInfo, projectManager, marker));
            materialized++;
        }

        materialized += reconcileSessions(workspaceInfo, projectManager, instanceDir, instanceId);
        return materialized;
    }

    private void materializeInstance(
            WorkspaceInfo workspaceInfo, ProjectManager projectManager, RemoteProjectInstance marker) {

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
        publishAudit(workspaceInfo, WorkspaceEventConverter.instanceCreated(
                clock.instant(), marker, workspaceInfo, WorkspaceEventCreator.WORKSPACE_RECONCILER_JOB));

        LOG.info("Instance materialized from workspace directory: instance_id={} project_id={}",
                marker.instanceId(), projectManager.info().id());
        JfrMessageEmitter.instanceCreated(marker.instanceId(), projectManager.info().name(), projectManager.info().id());
    }

    private int reconcileSessions(
            WorkspaceInfo workspaceInfo, ProjectManager projectManager, Path instanceDir, String instanceId) {

        Set<String> knownSessionIds = platformRepositories.findSessionsByInstanceId(instanceId).stream()
                .map(ProjectInstanceSessionInfo::sessionId)
                .collect(Collectors.toSet());

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
                    materializeSession(workspaceInfo, projectManager, session));
        }
        return newSessions.size();
    }

    private void materializeSession(
            WorkspaceInfo workspaceInfo, ProjectManager projectManager, RemoteProjectInstanceSession marker) {

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

        publishAudit(workspaceInfo, WorkspaceEventConverter.sessionCreated(
                clock.instant(), marker, workspaceInfo, WorkspaceEventCreator.WORKSPACE_RECONCILER_JOB));

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

    private void publishAudit(WorkspaceInfo workspaceInfo, WorkspaceEvent event) {
        workspaceEventPublisher.publish(workspaceInfo.id(), event);
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
