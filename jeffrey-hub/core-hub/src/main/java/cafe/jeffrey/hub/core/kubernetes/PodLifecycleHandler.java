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

package cafe.jeffrey.hub.core.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.core.streaming.SessionPaths;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reacts to lifecycle events of Jeffrey-labeled pods.
 *
 * <p><strong>Pod appears</strong> — the workspace named by the
 * {@code jeffrey.cafe/workspace} label is created immediately (when auto-create
 * is enabled), so the application's very first filesystem events are accepted
 * without any manual workspace setup and without waiting for the event-queue
 * fallback.</p>
 *
 * <p><strong>Pod terminates</strong> (deleted, or its phase turns
 * {@code Succeeded}/{@code Failed}) — every unfinished session of the instance
 * whose id equals the pod name is finished deterministically, replacing the
 * heartbeat-staleness inference. The finish timestamp still prefers the
 * clean-exit marker and last heartbeat written by the agent; the observation
 * time is only the last-resort fallback.</p>
 */
public class PodLifecycleHandler implements ResourceEventHandler<Pod> {

    private static final Logger LOG = LoggerFactory.getLogger(PodLifecycleHandler.class);

    private static final Set<String> TERMINAL_POD_PHASES = Set.of("Succeeded", "Failed");

    private final WorkspacesManager workspacesManager;
    private final HubPlatformRepositories platformRepositories;
    private final SessionFinisher sessionFinisher;
    private final HubJeffreyDirs jeffreyDirs;
    private final Clock clock;
    private final boolean autoCreateWorkspaces;

    public PodLifecycleHandler(
            WorkspacesManager workspacesManager,
            HubPlatformRepositories platformRepositories,
            SessionFinisher sessionFinisher,
            HubJeffreyDirs jeffreyDirs,
            Clock clock,
            boolean autoCreateWorkspaces) {

        this.workspacesManager = workspacesManager;
        this.platformRepositories = platformRepositories;
        this.sessionFinisher = sessionFinisher;
        this.jeffreyDirs = jeffreyDirs;
        this.clock = clock;
        this.autoCreateWorkspaces = autoCreateWorkspaces;
    }

    @Override
    public void onAdd(Pod pod) {
        try {
            ensureWorkspace(pod);
        } catch (Exception e) {
            LOG.error("Failed to process pod addition: pod={}", podName(pod), e);
        }
    }

    @Override
    public void onUpdate(Pod oldPod, Pod newPod) {
        try {
            if (isTerminalPhase(newPod) && !isTerminalPhase(oldPod)) {
                LOG.info("Pod reached terminal phase, finishing its sessions: pod={} phase={}",
                        podName(newPod), newPod.getStatus().getPhase());
                finishSessionsOfInstance(podName(newPod));
            }
        } catch (Exception e) {
            LOG.error("Failed to process pod update: pod={}", podName(newPod), e);
        }
    }

    @Override
    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
        try {
            LOG.info("Pod deleted, finishing its sessions: pod={} final_state_unknown={}",
                    podName(pod), deletedFinalStateUnknown);
            finishSessionsOfInstance(podName(pod));
        } catch (Exception e) {
            LOG.error("Failed to process pod deletion: pod={}", podName(pod), e);
        }
    }

    private void ensureWorkspace(Pod pod) {
        if (!autoCreateWorkspaces) {
            return;
        }

        Map<String, String> labels = pod.getMetadata().getLabels();
        String referenceId = labels != null ? labels.get(KubernetesConventions.LABEL_WORKSPACE) : null;
        if (referenceId == null || referenceId.isBlank()) {
            // The default workspace is owned by DefaultWorkspaceInitializer
            return;
        }

        if (workspacesManager.findByReferenceId(referenceId).isPresent()) {
            return;
        }

        WorkspaceInfo created = workspacesManager.create(
                WorkspacesManager.CreateWorkspaceRequest.builder()
                        .referenceId(referenceId)
                        .name(referenceId)
                        .build());
        LOG.info("Auto-created workspace for discovered pod: pod={} reference_id={} workspace_id={}",
                podName(pod), referenceId, created.id());
    }

    private void finishSessionsOfInstance(String instanceId) {
        for (WorkspaceManager workspaceManager : workspacesManager.findAll()) {
            for (ProjectManager projectManager : workspaceManager.projectsManager().findAll()) {
                finishProjectSessionsOfInstance(projectManager, instanceId);
            }
        }
    }

    private void finishProjectSessionsOfInstance(ProjectManager projectManager, String instanceId) {
        ProjectInfo projectInfo = projectManager.info();
        ProjectRepositoryRepository repositoryRepository =
                platformRepositories.newProjectRepositoryRepository(projectInfo.id());

        List<ProjectInstanceSessionInfo> unfinished =
                repositoryRepository.findUnfinishedSessionsByInstanceId(instanceId);
        if (unfinished.isEmpty()) {
            return;
        }

        List<RepositoryInfo> repositoryInfos = repositoryRepository.getAll();
        if (repositoryInfos.isEmpty()) {
            LOG.warn("No repository info found for project, cannot finish sessions: project_id={} instance_id={}",
                    projectInfo.id(), instanceId);
            return;
        }
        RepositoryInfo repositoryInfo = repositoryInfos.getFirst();

        for (ProjectInstanceSessionInfo sessionInfo : unfinished) {
            Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, sessionInfo);
            sessionFinisher.forceFinish(
                    repositoryRepository, projectInfo, sessionInfo, sessionPath, clock.instant());
            LOG.info("Session finished after pod termination: session_id={} instance_id={} project_id={}",
                    sessionInfo.sessionId(), instanceId, projectInfo.id());
        }
    }

    private static boolean isTerminalPhase(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getPhase() == null) {
            return false;
        }
        return TERMINAL_POD_PHASES.contains(pod.getStatus().getPhase());
    }

    private static String podName(Pod pod) {
        return pod.getMetadata().getName();
    }
}
