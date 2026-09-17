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
package cafe.jeffrey.hub.core.project.session;

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

import java.nio.file.Path;

/**
 * Which session is being finished: the project it belongs to, its row, and where its directory
 * is on the volume — the three things every finishing path needs together, and used to carry
 * as three positional parameters beside a repository derivable from the first.
 */
public record SessionRef(ProjectInfo project, ProjectInstanceSessionInfo session, Path path) {

    public SessionRef {
        if (project == null || session == null || path == null) {
            throw new IllegalArgumentException("A session reference names a project, a session and a path");
        }
    }
}
