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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;

/**
 * A repository checked out into a throwaway temp directory. {@link #close()} deletes the entire
 * directory, so callers should use it in a try-with-resources block. {@link #root()} is the working
 * tree root the AI tools are sandboxed to.
 *
 * <p>{@code resolvedRef} records which revision was actually analyzed — the requested commit when the
 * recording named one, otherwise the default branch's head. Every claim made about this checkout is
 * only true of that revision, so it travels with the result.</p>
 */
public record ClonedRepository(Path root, String resolvedRef, TempDirectory tempDirectory)
        implements AutoCloseable {

    @Override
    public void close() {
        tempDirectory.close();
    }
}
