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

package cafe.jeffrey.shared.common.filesystem;

import java.nio.file.Path;

public record TempDirectory(Path path) implements AutoCloseable {

    public TempDirectory {
        FileSystemUtils.createDirectories(path);
    }

    /**
     * A file directly inside this directory, by name. The name is taken as one path element and
     * nothing else: what lands here is often named by another machine — a hub's listing of a
     * session's files — and a name with a separator or a parent reference in it would place the
     * file wherever that machine chose. Such a name is refused rather than reduced, because a hub
     * that sends one is not sending what it listed.
     *
     * @throws IllegalArgumentException when the name is not a single path element
     */
    public Path resolve(String other) {
        Path resolved = path.resolve(other).normalize();
        if (!path.normalize().equals(resolved.getParent())) {
            throw new IllegalArgumentException("Not a plain file name: " + other);
        }
        return resolved;
    }

    @Override
    public void close() {
        FileSystemUtils.removeDirectory(path);
    }
}
