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

package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.session.SessionPaths;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.hub.persistence.api.ScopedConfigRepository;
import cafe.jeffrey.shared.common.config.ConfigScope;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Stores configuration values and keeps each scope's published file in step with them.
 *
 * <p><b>The database is the source of truth and the file is a projection of it.</b> Nothing ever
 * reads a file back: a value is stored first and published second, and publishing writes whatever
 * the database holds — including writing nothing, which removes the file.</p>
 *
 * <p>A change publishes immediately, in the same call, so a value saved in the UI reaches the
 * volume before the next JVM starts rather than up to a scheduler period later. The synchronizer
 * job exists for what that cannot cover — a hub that was down, a volume that was not mounted — and
 * both paths do the same idempotent thing, so their order never matters.</p>
 *
 * <p>A publish that fails is logged, not thrown: the value is stored, the caller's edit succeeded,
 * and the job will carry the file. Failing the call would tell an operator their change was lost
 * when it was not.</p>
 */
public class ScopedConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(ScopedConfigManager.class);

    private final Clock clock;
    private final ScopedConfigRepository repository;
    private final HubJeffreyDirs jeffreyDirs;
    private final WorkspacesManager workspacesManager;
    private final ScopedConfigPublisher publisher;

    public ScopedConfigManager(
            Clock clock,
            ScopedConfigRepository repository,
            HubJeffreyDirs jeffreyDirs,
            WorkspacesManager workspacesManager,
            ScopedConfigPublisher publisher) {

        this.clock = clock;
        this.repository = repository;
        this.jeffreyDirs = jeffreyDirs;
        this.workspacesManager = workspacesManager;
        this.publisher = publisher;
    }

    /** Stores one value and republishes its scope. */
    public List<ScopedConfigEntry> upsert(ScopedConfigKey key, ConfigType type, String value) {
        ConfigValueValidators.validate(type, value);
        repository.upsert(new ScopedConfigEntry(key, type, value, clock.instant()));
        LOG.info("Configuration value stored: scope={} workspace_id={} project_id={} type={}",
                key.scope(), key.workspaceId(), key.projectId(), type);
        return publish(key);
    }

    /** Removes one value and republishes its scope, deleting the file when nothing is left. */
    public List<ScopedConfigEntry> delete(ScopedConfigKey key, ConfigType type) {
        repository.delete(key, type);
        LOG.info("Configuration value removed: scope={} workspace_id={} project_id={} type={}",
                key.scope(), key.workspaceId(), key.projectId(), type);
        return publish(key);
    }

    /** Removes everything a scope holds, for a workspace or project that is going away. */
    public void deleteAll(ScopedConfigKey key) {
        repository.deleteAll(key);
        publish(key);
    }

    /** What one scope holds, empty when it holds nothing. */
    public List<ScopedConfigEntry> find(ScopedConfigKey key) {
        return repository.find(key);
    }

    /**
     * Everything that applies to a workspace — the global scope, the workspace's own and each of
     * its projects. Each entry carries the scope it belongs to, so a caller groups by it.
     */
    public List<ScopedConfigEntry> findForWorkspace(String workspaceId) {
        return repository.findForWorkspace(workspaceId);
    }

    /** Writes a scope's stored values out, whatever they are. */
    public List<ScopedConfigEntry> publish(ScopedConfigKey key) {
        List<ScopedConfigEntry> entries = repository.find(key);

        Optional<Path> scopeDir = scopeDirectory(key);
        if (scopeDir.isEmpty()) {
            LOG.debug("Nowhere to publish this scope yet: scope={} workspace_id={} project_id={}",
                    key.scope(), key.workspaceId(), key.projectId());
            return entries;
        }

        try {
            publisher.publish(scopeDir.get(), entries);
        } catch (RuntimeException e) {
            // The values are stored; the synchronizer republishes. Failing here would report a
            // successful edit as lost.
            LOG.error("Failed to publish configuration, the synchronizer will retry: "
                            + "scope={} workspace_id={} project_id={}",
                    key.scope(), key.workspaceId(), key.projectId(), e);
        }
        return entries;
    }

    /**
     * Where a scope's file belongs, empty when it has no folder to write into — never an error.
     *
     * <p>A workspace's folder is created when it is missing, deliberately. Its path is derived, not
     * discovered, and it is the same path the provisioner would create; creating it here is what
     * lets a workspace's very first JVM already find a file, instead of running on defaults because
     * nothing had happened in that workspace yet.</p>
     *
     * <p>A project's folder is never created. A project exists in the hub only because the
     * provisioner declared it by writing into its directory, so a missing directory means the
     * project is gone rather than new, and creating it would resurrect a shell of something that
     * was deleted.</p>
     */
    private Optional<Path> scopeDirectory(ScopedConfigKey key) {
        return switch (key.scope()) {
            case GLOBAL -> Optional.of(jeffreyDirs.workspaces());
            case WORKSPACE -> workspaceDirectory(key.workspaceId());
            case PROJECT -> projectDirectory(key.workspaceId(), key.projectId());
        };
    }

    private Optional<Path> workspaceDirectory(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .map(WorkspaceManager::resolveInfo)
                .map(info -> FileSystemUtils.createDirectories(info.location().toPath()));
    }

    private Optional<Path> projectDirectory(String workspaceId, String projectId) {
        Optional<Path> directory = workspacesManager.findById(workspaceId)
                .flatMap(workspace -> workspace.projectsManager().project(projectId))
                .map(ProjectManager::repositoryManager)
                .flatMap(RepositoryManager::info)
                .map(this::projectPath)
                .filter(FileSystemUtils::isDirectory);

        if (directory.isEmpty()) {
            LOG.debug("No project directory to publish into: workspace_id={} project_id={}",
                    workspaceId, projectId);
        }
        return directory;
    }

    private Path projectPath(RepositoryInfo repositoryInfo) {
        return SessionPaths.project(jeffreyDirs.workspaces(), repositoryInfo);
    }

    /** The scope a workspace's own values live at, spelled once. */
    public static ScopedConfigKey workspaceKey(String workspaceId) {
        return new ScopedConfigKey(ConfigScope.WORKSPACE, workspaceId, null);
    }
}
