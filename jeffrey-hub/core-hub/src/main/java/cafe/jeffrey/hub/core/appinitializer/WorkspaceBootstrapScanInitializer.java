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

package cafe.jeffrey.hub.core.appinitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Reconciles every workspace tree once at startup.
 *
 * <p>Steady-state discovery is driven by the pending index, which only names what the
 * provisioner announced <em>while the hub was able to consume it</em>. A tree that was
 * provisioned by an older CLI, restored from a snapshot, or populated while this hub was down
 * has declarations on disk that nothing will announce again. This one-off scan is what makes
 * those visible; it is not a periodic backstop and costs nothing after startup.</p>
 *
 * <p>Disable with {@code jeffrey.hub.workspaces.bootstrap-scan-on-startup=false} for strictly
 * index-driven discovery.</p>
 */
public class WorkspaceBootstrapScanInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceBootstrapScanInitializer.class);

    private final WorkspacesManager workspacesManager;
    private final WorkspaceReconciler reconciler;
    private final HubJeffreyDirs jeffreyDirs;
    private final boolean enabled;

    public WorkspaceBootstrapScanInitializer(
            WorkspacesManager workspacesManager,
            WorkspaceReconciler reconciler,
            HubJeffreyDirs jeffreyDirs,
            boolean enabled) {

        this.workspacesManager = workspacesManager;
        this.reconciler = reconciler;
        this.jeffreyDirs = jeffreyDirs;
        this.enabled = enabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        run();
    }

    public void run() {
        if (!enabled) {
            LOG.info("Workspace bootstrap scan disabled, discovery is driven only by the pending index");
            return;
        }

        var scan = Measuring.s(this::scanAllWorkspaces);
        LOG.info("Workspace bootstrap scan completed: materialized={} duration_in_ms={}",
                scan.entity(), scan.duration().toMillis());
    }

    private int scanAllWorkspaces() {
        int materialized = 0;
        for (Path workspaceDir : WorkspaceReconciler.childDirectories(jeffreyDirs.workspaces())) {
            try {
                // Only workspaces the hub already knows are scanned: auto-creating one is a
                // decision for the announced path, not for whatever happens to be on the volume
                Optional<WorkspaceManager> workspaceOpt =
                        workspacesManager.findByReferenceId(workspaceDir.getFileName().toString());
                if (workspaceOpt.isPresent()) {
                    materialized += reconciler.reconcile(workspaceOpt.get().projectsManager(), workspaceDir);
                }
            } catch (Exception e) {
                LOG.error("Bootstrap scan failed for workspace directory, skipping: workspace_dir={}",
                        workspaceDir, e);
            }
        }
        return materialized;
    }
}
