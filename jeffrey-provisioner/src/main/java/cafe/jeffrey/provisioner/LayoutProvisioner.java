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
     * Creates the session directory and the two subdirectories the agent writes into: the JFR
     * streaming repository and the heartbeat directory.
     */
    public SessionLayout provisionSession(ProjectLayout layout, Path sessionPath) throws IOException {
        Path session = createDirectories(sessionPath);
        createDirectories(session.resolve(JeffreyLayout.STREAMING_REPO_DIR));
        createDirectories(session.resolve(HeartbeatConstants.HEARTBEAT_DIR));

        LOG.debug("Session directory created: sessionPath={}", session);
        return layout.withSession(session);
    }

    public static Path createDirectories(Path path) throws IOException {
        return Files.exists(path) ? path : Files.createDirectories(path);
    }
}
