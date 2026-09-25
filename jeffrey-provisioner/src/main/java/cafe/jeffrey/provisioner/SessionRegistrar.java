/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

        return new ProvisionedSession(instanceId, sessionId, nextOrder(instancePath), sessionLayout);
    }

    /** Writes the session marker, which is what makes the session visible to the hub. */
    public void recordSession(InitConfig config, ProvisionedSession session) {
        repository.addSession(
                session.sessionId(),
                session.instanceId(),
                session.order(),
                session.layout().session(),
                config.isHeartbeatEnabled());
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
