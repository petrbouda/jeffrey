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
 * Where a provisioner run put things. Everything downstream — the placeholder source, the
 * {@code .env} file, the argfile — needs some subset of these paths, and each used to restate its
 * own subset as a separate record built from the same locals.
 *
 * @param jeffreyHome null when the run was configured with an explicit workspaces directory
 *                    instead, in which case there is no Jeffrey home to speak of
 */
public record SessionLayout(
        Path jeffreyHome,
        Path workspaces,
        Path workspace,
        Path project,
        Path session) {

    public SessionLayout {
        if (workspaces == null || workspace == null || project == null || session == null) {
            throw new IllegalArgumentException("Only jeffreyHome may be absent from a session layout");
        }
    }

    /** Whether this run laid its directories out under a Jeffrey home. */
    public boolean hasJeffreyHome() {
        return jeffreyHome != null;
    }

    /** The file the profiler writes recordings to, by the session's own naming template. */
    public Path recordingFilePattern(String template) {
        return session.resolve(template);
    }
}
