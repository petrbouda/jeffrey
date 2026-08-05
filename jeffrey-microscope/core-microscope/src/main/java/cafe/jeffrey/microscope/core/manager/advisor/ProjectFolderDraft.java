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

package cafe.jeffrey.microscope.core.manager.advisor;

import java.nio.file.Path;

/**
 * The editable half of a project folder: what the user typed, trimmed and checked. Identity fields
 * (id, creation time) are the manager's business, so they are absent here.
 *
 * <p>The path must be absolute: the Advisor resolves it long after this request, in a process whose
 * working directory is not the user's, so a relative path would read a different folder than the one
 * that was typed.
 */
public record ProjectFolderDraft(String name, String path) {

    private static final String NAME_REQUIRED = "Project name is required";
    private static final String PATH_REQUIRED = "Local folder is required";
    private static final String PATH_NOT_ABSOLUTE = "Local folder must be an absolute path: path=";

    public ProjectFolderDraft {
        name = name == null ? "" : name.trim();
        path = path == null ? "" : path.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException(NAME_REQUIRED);
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException(PATH_REQUIRED);
        }
        // InvalidPathException is an IllegalArgumentException, so an unparseable path fails the same way.
        if (!Path.of(path).isAbsolute()) {
            throw new IllegalArgumentException(PATH_NOT_ABSOLUTE + path);
        }
    }
}
