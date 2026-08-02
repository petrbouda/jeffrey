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

package cafe.jeffrey.profile.advisor.run;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Answers one question for everything the model cites: does this path name a file that really exists
 * inside the analyzed checkout? Both {@link ClaimGrounder} and {@link PatchBuilder} need it and must
 * answer it identically — a claim that counts as grounded and a patch that counts as applicable have
 * to agree about what "in the source folder" means.
 *
 * <p>Containment is checked after normalization, so {@code ../../etc/passwd} resolves out of the root
 * and is rejected. A null root (no source folder configured) contains nothing.</p>
 */
public final class SourcePaths {

    private final Path root;

    public SourcePaths(Path root) {
        this.root = root == null ? null : root.toAbsolutePath().normalize();
    }

    /**
     * True when {@code relativePath} resolves to a regular file inside the root. A path that escapes
     * the root is false rather than an error: the caller's job is to report an unverified citation,
     * not to fail the run over one.
     */
    public boolean containsFile(String relativePath) {
        if (root == null || relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            Path resolved = root.resolve(relativePath).normalize();
            return resolved.startsWith(root) && Files.isRegularFile(resolved);
        } catch (InvalidPathException e) {
            // Not a path this filesystem can express (a NUL byte, an illegal character on Windows).
            return false;
        }
    }
}
