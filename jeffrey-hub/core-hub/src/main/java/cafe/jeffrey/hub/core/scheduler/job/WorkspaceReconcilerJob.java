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

package cafe.jeffrey.hub.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Discovers new projects, instances and sessions by scanning the workspace directory tree
 * and diffing the on-disk declarations (marker files written by the provisioner) against
 * the database. Replaces the former CLI event-file pipeline
 * (folder queue → persistent queue → consumers) with a single create-only scan.
 *
 * <p>Assumes a single hub instance owns the database — two hubs scanning the same volume
 * would race their materializations. A failed workspace is logged and skipped; the scan of
 * the remaining workspaces continues and the failed one converges on a later tick.</p>
 */
public class WorkspaceReconcilerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceReconcilerJob.class);

    private final WorkspacesManager workspacesManager;
    private final WorkspaceReconciler reconciler;
    private final HubJeffreyDirs jeffreyDirs;
    private final DefaultWorkspaceProperties defaultWorkspaceProperties;
    private final boolean autoCreateWorkspaces;
    private final Duration period;

    public WorkspaceReconcilerJob(
            WorkspacesManager workspacesManager,
            WorkspaceReconciler reconciler,
            HubJeffreyDirs jeffreyDirs,
            DefaultWorkspaceProperties defaultWorkspaceProperties,
            boolean autoCreateWorkspaces,
            Duration period) {

        this.workspacesManager = workspacesManager;
        this.reconciler = reconciler;
        this.jeffreyDirs = jeffreyDirs;
        this.defaultWorkspaceProperties = defaultWorkspaceProperties;
        this.autoCreateWorkspaces = autoCreateWorkspaces;
        this.period = period;
    }

    @Override
    public void execute(JobContext context) {
        int materialized = 0;
        for (Path workspaceDir : WorkspaceReconciler.childDirectories(jeffreyDirs.workspaces())) {
            try {
                materialized += reconcileWorkspaceDirectory(workspaceDir);
            } catch (Exception e) {
                LOG.error("Failed to reconcile workspace directory, skipping: workspace_dir={}", workspaceDir, e);
            }
        }

        if (materialized > 0) {
            LOG.info("Workspace reconciliation materialized new entities: count={}", materialized);
        }
    }

    private int reconcileWorkspaceDirectory(Path workspaceDir) {
        String referenceId = workspaceDir.getFileName().toString();
        Optional<WorkspaceManager> workspaceOpt = workspacesManager.findByReferenceId(referenceId);

        WorkspaceManager workspaceManager;
        if (workspaceOpt.isPresent()) {
            workspaceManager = workspaceOpt.get();
        } else if (referenceId.equals(defaultWorkspaceProperties.getReferenceId())) {
            // The default workspace is owned by DefaultWorkspaceInitializer — never auto-create it here
            LOG.error("Default workspace missing, skipping its directory: reference_id={}", referenceId);
            return 0;
        } else if (autoCreateWorkspaces && containsProjectDeclaration(workspaceDir)) {
            WorkspaceInfo created = workspacesManager.create(
                    WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId(referenceId)
                            .name(referenceId)
                            .build());
            LOG.info("Auto-created workspace for discovered directory: reference_id={} workspace_id={}",
                    referenceId, created.id());
            workspaceManager = workspacesManager.findByReferenceId(referenceId).orElseThrow();
        } else {
            LOG.debug("Workspace not registered, skipping directory: reference_id={}", referenceId);
            return 0;
        }

        return reconciler.reconcile(
                workspaceManager.localInfo(), workspaceManager.projectsManager(), workspaceDir);
    }

    /**
     * A stray directory under {@code workspaces/} must not become a workspace — only one
     * that actually declares at least one project.
     */
    private static boolean containsProjectDeclaration(Path workspaceDir) {
        return WorkspaceReconciler.childDirectories(workspaceDir).stream()
                .anyMatch(projectDir -> Files.isRegularFile(projectDir.resolve(JeffreyLayout.PROJECT_INFO_FILE)));
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_RECONCILER;
    }
}
