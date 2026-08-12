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
import cafe.jeffrey.hub.core.scheduler.ManuallyTriggerable;
import cafe.jeffrey.hub.core.workspace.reconcile.WorkspaceReconciler;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.model.job.JobType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.pendingindex.PendingIndex;
import cafe.jeffrey.shared.pendingindex.PendingIndexEntry;
import cafe.jeffrey.shared.pendingindex.PendingIndexEntryParser;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Discovers new projects, instances and sessions from what the provisioner announced in each
 * workspace's pending index ({@link JeffreyLayout#PENDING_DIR}). An empty index means the
 * workspace is untouched since the last tick, and the job does no further I/O and issues no
 * queries for it — which is what lets the tick run often.
 *
 * <p>An index entry is a <em>pointer</em>: it names a path relative to the workspace directory.
 * Whatever depth it points at, the project subtree owning it is reconciled, so the marker files
 * remain the only description of an entity and the index can never disagree with them.</p>
 *
 * <p>An entry is removed only once its project reconciled successfully <em>and</em> declared
 * itself with a readable marker. The provisioner writes the marker before the hint, so a hint
 * read mid-write is kept and retried rather than consumed.</p>
 *
 * <p>Assumes a single hub instance owns the database — two hubs consuming the same index would
 * race their materializations. A failed workspace is logged and skipped; its entries survive and
 * the workspace converges on a later tick.</p>
 */
public class WorkspaceReconcilerJob implements Job, ManuallyTriggerable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceReconcilerJob.class);

    /** Entries are plain relative paths; blank content is a not-yet-readable entry. */
    private static final PendingIndexEntryParser<String> RELATIVE_PATH =
            (_, content) -> Optional.of(content.trim()).filter(path -> !path.isEmpty());

    private final WorkspacesManager workspacesManager;
    private final WorkspaceReconciler reconciler;
    private final HubJeffreyDirs jeffreyDirs;
    private final DefaultWorkspaceProperties defaultWorkspaceProperties;
    private final boolean autoCreateWorkspaces;
    private final Clock clock;
    private final Duration period;

    public WorkspaceReconcilerJob(
            WorkspacesManager workspacesManager,
            WorkspaceReconciler reconciler,
            HubJeffreyDirs jeffreyDirs,
            DefaultWorkspaceProperties defaultWorkspaceProperties,
            boolean autoCreateWorkspaces,
            Clock clock,
            Duration period) {

        this.workspacesManager = workspacesManager;
        this.reconciler = reconciler;
        this.jeffreyDirs = jeffreyDirs;
        this.defaultWorkspaceProperties = defaultWorkspaceProperties;
        this.autoCreateWorkspaces = autoCreateWorkspaces;
        this.clock = clock;
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

    /**
     * Walks every workspace tree in full, ignoring the pending index entirely.
     *
     * <p>The index only names what was announced while a hub was running to consume it. A tree
     * provisioned by an older CLI, restored from a snapshot, or written while this hub was down has
     * declarations on disk that nothing will announce again — this is what finds them. It is
     * create-only like every other path here, so running it when nothing is missing changes
     * nothing.</p>
     */
    @Override
    public String runManually() {
        int materialized = 0;
        int scanned = 0;
        for (Path workspaceDir : WorkspaceReconciler.childDirectories(jeffreyDirs.workspaces())) {
            Optional<WorkspaceManager> workspaceOpt = workspacesManager.findByReferenceId(
                    workspaceDir.getFileName().toString());
            if (workspaceOpt.isEmpty()) {
                // Auto-creating a workspace is a decision for an announced path, never for
                // whatever happens to be sitting on the volume
                continue;
            }
            scanned++;
            materialized += reconciler.reconcile(workspaceOpt.get().projectsManager(), workspaceDir);
        }

        if (materialized == 0) {
            return "Nothing new in " + scanned + (scanned == 1 ? " workspace" : " workspaces");
        }
        return materialized + (materialized == 1 ? " entity" : " entities")
                + " from " + scanned + (scanned == 1 ? " workspace" : " workspaces");
    }

    private int reconcileWorkspaceDirectory(Path workspaceDir) {
        PendingIndex pendingIndex = pendingIndex(workspaceDir);
        List<PendingIndexEntry<String>> entries = pendingIndex.list(RELATIVE_PATH);
        if (entries.isEmpty()) {
            return 0;
        }

        Optional<WorkspaceManager> workspaceOpt = resolveWorkspace(workspaceDir);
        if (workspaceOpt.isEmpty()) {
            return 0;
        }

        int materialized = 0;
        for (Map.Entry<String, List<PendingIndexEntry<String>>> announced
                : groupByProjectName(entries).entrySet()) {

            Path projectDir = workspaceDir.resolve(announced.getKey());
            WorkspaceReconciler.Result result = reconciler.reconcileProjectDirectory(
                    workspaceOpt.get().projectsManager(), projectDir);

            if (result.declarationSeen()) {
                announced.getValue().forEach(entry -> pendingIndex.remove(entry.filePath()));
            } else {
                LOG.debug("Keeping pending entries, project declaration not readable yet: project_dir={}",
                        projectDir);
            }
            materialized += result.materialized();
        }
        return materialized;
    }

    /**
     * Groups entries by the project directory they point at — the first path segment. Several
     * entries for one project (its instances and sessions) reconcile that project once, and are
     * then removed together.
     */
    private static Map<String, List<PendingIndexEntry<String>>> groupByProjectName(
            List<PendingIndexEntry<String>> entries) {

        Map<String, List<PendingIndexEntry<String>>> byProject = new LinkedHashMap<>();
        for (PendingIndexEntry<String> entry : entries) {
            Path announced = Path.of(entry.parsed()).normalize();
            if (announced.getNameCount() == 0 || announced.startsWith("..")) {
                LOG.warn("Ignoring pending entry that does not name a project: entry={} content={}",
                        entry.filename(), entry.parsed());
                continue;
            }
            byProject.computeIfAbsent(announced.getName(0).toString(), _ -> new ArrayList<>()).add(entry);
        }
        return byProject;
    }

    private Optional<WorkspaceManager> resolveWorkspace(Path workspaceDir) {
        String referenceId = workspaceDir.getFileName().toString();
        Optional<WorkspaceManager> workspaceOpt = workspacesManager.findByReferenceId(referenceId);
        if (workspaceOpt.isPresent()) {
            return workspaceOpt;
        }

        if (referenceId.equals(defaultWorkspaceProperties.getReferenceId())) {
            // The default workspace is owned by DefaultWorkspaceInitializer — never auto-create it here
            LOG.error("Default workspace missing, skipping its directory: reference_id={}", referenceId);
            return Optional.empty();
        }

        if (!autoCreateWorkspaces) {
            LOG.debug("Workspace not registered, skipping directory: reference_id={}", referenceId);
            return Optional.empty();
        }

        // Pending entries are what makes a directory a workspace with content — a stray
        // directory under workspaces/ announces nothing and never becomes one
        WorkspaceInfo created = workspacesManager.create(
                WorkspacesManager.CreateWorkspaceRequest.builder()
                        .referenceId(referenceId)
                        .name(referenceId)
                        .build());
        LOG.info("Auto-created workspace for announced directory: reference_id={} workspace_id={}",
                referenceId, created.id());
        return workspacesManager.findByReferenceId(referenceId);
    }

    private PendingIndex pendingIndex(Path workspaceDir) {
        return new PendingIndex(workspaceDir.resolve(JeffreyLayout.PENDING_DIR), clock);
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
