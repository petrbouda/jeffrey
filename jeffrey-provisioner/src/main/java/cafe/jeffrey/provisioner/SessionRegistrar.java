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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.model.repository.RemoteProject;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import cafe.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Declares a run's project, instance and session to the hub by writing the marker files it
 * reconciles from.
 *
 * <p>Project and instance are found-or-created, so repeated runs of the same application reuse
 * them; the session is always new. Registration is two steps because the session marker records
 * the resolved profiler command, which is not known until the session directory exists and the
 * JVM options have been built around it.
 */
public class SessionRegistrar {

    private final FileSystemRepository repository;
    private final LayoutProvisioner layoutProvisioner;

    public SessionRegistrar(FileSystemRepository repository, LayoutProvisioner layoutProvisioner) {
        this.repository = repository;
        this.layoutProvisioner = layoutProvisioner;
    }

    /**
     * Ensures the project and instance exist, then creates this run's session directory and
     * works out where it sits in the instance's order.
     */
    public ProvisionedSession openSession(InitConfig config, ProjectLayout layout) throws IOException {
        String projectId = findOrCreateProject(config, layout);

        String instanceId = config.getInstanceName();
        Path instancePath = layout.project().resolve(instanceId);
        findOrCreateInstance(config, projectId, instanceId, instancePath);

        String sessionId = IDGenerator.generate();
        SessionLayout sessionLayout =
                layoutProvisioner.provisionSession(layout, instancePath.resolve(sessionId));

        return new ProvisionedSession(projectId, instanceId, sessionId, nextOrder(instancePath), sessionLayout);
    }

    /** Writes the session marker, which is what makes the session visible to the hub. */
    public void recordSession(
            InitConfig config,
            ProvisionedSession session,
            ProfilerSettingsResolver.ResolvedProfilerSettings resolvedSettings) {

        repository.addSession(
                session.sessionId(),
                session.projectId(),
                config.getWorkspaceRefId(),
                session.instanceId(),
                session.order(),
                session.layout().session(),
                resolvedSettings);
    }

    private String findOrCreateProject(InitConfig config, ProjectLayout layout) {
        Optional<RemoteProject> existing = repository.findProject(layout.project());
        if (existing.isPresent()) {
            return existing.get().projectId();
        }

        String projectId = IDGenerator.generate();
        // The configured workspaces-dir, deliberately, and not the resolved path: in jeffrey-home
        // mode it is null, which tells the hub to resolve the relative paths recorded alongside it
        // against its own workspaces directory. Writing the absolute path this run computed would
        // bake this container's mount point into a marker another container reads, and the two
        // need not see the shared volume at the same place.
        repository.addProject(
                projectId,
                config.getProjectName(),
                config.getProjectLabel(),
                config.getWorkspaceRefId(),
                config.getWorkspacesDir(),
                config.resolveRepositoryType(),
                config.getAttributes(),
                layout.project());
        return projectId;
    }

    private void findOrCreateInstance(
            InitConfig config, String projectId, String instanceId, Path instancePath) throws IOException {

        Optional<RemoteProjectInstance> existing = repository.findInstance(instancePath);
        if (existing.isPresent()) {
            return;
        }

        LayoutProvisioner.createDirectories(instancePath);
        repository.addInstance(instanceId, projectId, config.getWorkspaceRefId(), instancePath);
    }

    /** One past the highest order already recorded in this instance; the first session is 1. */
    private int nextOrder(Path instancePath) {
        List<RemoteProjectInstanceSession> existing = repository.findSessionsInInstance(instancePath);
        return existing.stream()
                .mapToInt(RemoteProjectInstanceSession::order)
                .max()
                .orElse(0) + 1;
    }
}
