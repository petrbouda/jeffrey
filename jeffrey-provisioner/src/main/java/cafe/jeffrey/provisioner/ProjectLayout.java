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

import java.nio.file.Path;
import java.time.ZoneId;

/**
 * The directories a run shares with every other run of the same project — everything above the
 * session. Becomes a {@link SessionLayout} once this run's session directory exists.
 */
public record ProjectLayout(
        Path jeffreyHome,
        Path workspaces,
        Path workspace,
        Path project) {

    public ProjectLayout {
        if (jeffreyHome == null || workspaces == null || workspace == null || project == null) {
            throw new IllegalArgumentException("A project layout needs every directory above the session");
        }
    }

    /** @param zone the zone the profiled machine runs in, which its recordings' names are local to */
    public SessionLayout withSession(Path session, ZoneId zone) {
        return new SessionLayout(jeffreyHome, workspaces, workspace, project, session, zone);
    }
}
