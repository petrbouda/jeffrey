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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Declares a run's project, instance and session to the hub by writing the marker files it
 * lists them from.
 *
 * <p>Project and instance are found-or-created, so repeated runs of the same application reuse
 * them; the session is always new. Registration is two steps because the session marker is what
 * makes the session visible to the hub, so it is written only once the JVM options have been
 * built around the session directory.
 */
public class SessionRegistrar {

    private final FileSystemRepository repository;
    private final LayoutProvisioner layoutProvisioner;

    public SessionRegistrar(FileSystemRepository repository, LayoutProvisioner layoutProvisioner) {
        this.repository = repository;
        this.layoutProvisioner = layoutProvisioner;
    }

    /** Ensures the project and instance exist, then creates this run's session directory. */
    public ProvisionedSession openSession(InitConfig config, ProjectLayout layout) throws IOException {
        ensureProject(config, layout);

        String instanceId = config.getInstanceName();
        Path instancePath = layout.project().resolve(instanceId);
        ensureInstance(instanceId, instancePath);

        String sessionId = IDGenerator.generate();
        SessionLayout sessionLayout =
                layoutProvisioner.provisionSession(layout, instancePath.resolve(sessionId));

        return new ProvisionedSession(instanceId, sessionId, sessionLayout);
    }

    /** Writes the session marker, which is what makes the session visible to the hub. */
    public void recordSession(ProvisionedSession session) {
        repository.addSession(session.sessionId(), session.layout().session());
    }

    private void ensureProject(InitConfig config, ProjectLayout layout) {
        if (repository.findProject(layout.project()).isPresent()) {
            return;
        }

        repository.addProject(
                IDGenerator.generate(),
                config.getProjectName(),
                config.getAttributes(),
                layout.project());
    }

    private void ensureInstance(String instanceId, Path instancePath) throws IOException {
        if (repository.findInstance(instancePath).isPresent()) {
            return;
        }

        LayoutProvisioner.createDirectories(instancePath);
        repository.addInstance(instanceId, instancePath);
    }
}
