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

package pbouda.jeffrey.server.core.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionPathsTest {

    @Nested
    class Resolve {

        @Test
        void usesDefaultWorkspacesPathWhenNull(@TempDir Path homeDir) {
            var jeffreyDirs = new ServerJeffreyDirs(homeDir);
            var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, null, "workspace-1", "project-1");
            var sessionInfo = sessionInfo("session-2025");

            Path resolved = SessionPaths.resolve(jeffreyDirs, repoInfo, sessionInfo);

            assertEquals(homeDir.resolve("workspaces/workspace-1/project-1/session-2025"), resolved);
        }

        @Test
        void usesCustomWorkspacesPathWhenProvided(@TempDir Path homeDir) {
            var jeffreyDirs = new ServerJeffreyDirs(homeDir);
            var customPath = homeDir.resolve("custom/workspaces").toString();
            var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, customPath, "workspace-1", "project-1");
            var sessionInfo = sessionInfo("session-2025");

            Path resolved = SessionPaths.resolve(jeffreyDirs, repoInfo, sessionInfo);

            assertEquals(Path.of(customPath, "workspace-1", "project-1", "session-2025"), resolved);
        }
    }

    @Nested
    class ResolveStreamingRepo {

        @Test
        void appendsStreamingRepoDir(@TempDir Path homeDir) {
            var jeffreyDirs = new ServerJeffreyDirs(homeDir);
            var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, null, "workspace-1", "project-1");
            var sessionInfo = sessionInfo("session-2025");
            var session = new SessionWithRepository("proj-1", repoInfo, sessionInfo);

            Path resolved = SessionPaths.resolveStreamingRepo(jeffreyDirs, session);

            assertEquals(
                    homeDir.resolve("workspaces/workspace-1/project-1/session-2025/streaming-repo"),
                    resolved);
        }
    }

    private static ProjectInstanceSessionInfo sessionInfo(String relativeSessionPath) {
        return new ProjectInstanceSessionInfo(
                "session-001", "repo-1", "instance-1", 0,
                Path.of(relativeSessionPath), null, null, null, null);
    }
}
