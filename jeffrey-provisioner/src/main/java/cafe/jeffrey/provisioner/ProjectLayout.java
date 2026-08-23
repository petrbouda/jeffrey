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

import java.nio.file.Path;

/**
 * The directories a run shares with every other run of the same project — everything above the
 * session. Becomes a {@link SessionLayout} once this run's session directory exists.
 *
 * @param jeffreyHome null when the run was configured with an explicit workspaces directory
 *                    instead, in which case there is no Jeffrey home to speak of
 */
public record ProjectLayout(
        Path jeffreyHome,
        Path workspaces,
        Path workspace,
        Path project) {

    public ProjectLayout {
        if (workspaces == null || workspace == null || project == null) {
            throw new IllegalArgumentException("Only jeffreyHome may be absent from a project layout");
        }
    }

    public SessionLayout withSession(Path session) {
        return new SessionLayout(jeffreyHome, workspaces, workspace, project, session);
    }
}
