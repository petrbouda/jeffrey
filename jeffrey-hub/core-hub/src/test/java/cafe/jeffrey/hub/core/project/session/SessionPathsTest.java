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

package cafe.jeffrey.hub.core.project.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionPathsTest {

    @Nested
    class Resolve {

        @Test
        void usesDefaultWorkspacesPathWhenNull(@TempDir Path homeDir) {
            var jeffreyDirs = new HubJeffreyDirs(homeDir);
            var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, null, "workspace-1", "project-1");
            var sessionInfo = sessionInfo("session-2025");

            Path resolved = SessionPaths.resolve(jeffreyDirs, repoInfo, sessionInfo);

            assertEquals(homeDir.resolve("workspaces/workspace-1/project-1/session-2025"), resolved);
        }

        @Test
        void usesCustomWorkspacesPathWhenProvided(@TempDir Path homeDir) {
            var jeffreyDirs = new HubJeffreyDirs(homeDir);
            var customPath = homeDir.resolve("custom/workspaces").toString();
            var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, customPath, "workspace-1", "project-1");
            var sessionInfo = sessionInfo("session-2025");

            Path resolved = SessionPaths.resolve(jeffreyDirs, repoInfo, sessionInfo);

            assertEquals(Path.of(customPath, "workspace-1", "project-1", "session-2025"), resolved);
        }
    }

    private static ProjectInstanceSessionInfo sessionInfo(String relativeSessionPath) {
        return ProjectInstanceSessionInfo.notRetained(
                "session-001", "repo-1", "instance-1", 0,
                Path.of(relativeSessionPath), null, null, null);
    }
}
