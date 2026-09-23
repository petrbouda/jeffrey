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

import cafe.jeffrey.shared.common.HeartbeatConstants;
import cafe.jeffrey.shared.common.JeffreyLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates the directory tree a run needs, up to and including its session directory.
 *
 * <p>The workspaces root comes either from an explicit {@code workspaces-dir} or from
 * {@code jeffrey-home}, which is the only place that choice is acted on.
 */
public class LayoutProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(LayoutProvisioner.class);

    /** Everything above the session: shared by every run of the same project. */
    public ProjectLayout provisionProject(InitConfig config) throws IOException {
        Path jeffreyHome = null;
        Path workspaces;

        if (config.useJeffreyHome()) {
            jeffreyHome = createDirectories(Path.of(config.getJeffreyHome()));
            workspaces = createDirectories(jeffreyHome.resolve(JeffreyLayout.WORKSPACES_DIR));
        } else {
            workspaces = createDirectories(Path.of(config.getWorkspacesDir()));
        }

        Path workspace = createDirectories(workspaces.resolve(config.getWorkspaceRefId()));
        Path project = createDirectories(workspace.resolve(config.getProjectName()));

        LOG.debug("Directories created: workspacesPath={} workspacePath={}", workspaces, workspace);
        return new ProjectLayout(jeffreyHome, workspaces, workspace, project);
    }

    /**
     * Creates the session directory and the heartbeat directory, which is laid down whether or not
     * the session declared liveness: the library creates it too, and a directory that is already
     * there is one fewer thing to fail on a read-only or slow mount.
     */
    public SessionLayout provisionSession(ProjectLayout layout, Path sessionPath) throws IOException {
        Path session = createDirectories(sessionPath);
        createDirectories(session.resolve(HeartbeatConstants.HEARTBEAT_DIR));

        LOG.debug("Session directory created: sessionPath={}", session);
        return layout.withSession(session);
    }

    public static Path createDirectories(Path path) throws IOException {
        return Files.exists(path) ? path : Files.createDirectories(path);
    }
}
