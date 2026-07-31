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

package cafe.jeffrey.profile.advisor.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads the commit a source folder is currently on, by parsing {@code .git} directly rather than
 * shelling out or linking a git library. The question asked here is small enough that the plumbing
 * files answer it: {@code HEAD} either names a ref to follow or is a detached object id, and a ref is
 * either a loose file or a line in {@code packed-refs}.
 *
 * <p>Every failure path returns empty rather than throwing. A folder that is not a checkout is a
 * perfectly normal input — the advisor still runs, it just reports the commit as unknown.</p>
 */
public final class LocalGitProbe {

    private static final Logger LOG = LoggerFactory.getLogger(LocalGitProbe.class);

    private static final String GIT_DIR = ".git";
    private static final String HEAD_FILE = "HEAD";
    private static final String PACKED_REFS_FILE = "packed-refs";
    private static final String GITDIR_PREFIX = "gitdir:";
    private static final String REF_PREFIX = "ref:";
    private static final String COMMENT_PREFIX = "#";
    private static final String PEELED_PREFIX = "^";
    private static final int OBJECT_ID_LENGTH = 40;

    private LocalGitProbe() {
    }

    /**
     * The commit id the working tree at {@code root} is on, or empty when {@code root} is not a git
     * checkout or the plumbing says something this probe does not understand.
     */
    public static Optional<String> headCommit(Path root) {
        try {
            Optional<Path> gitDir = resolveGitDir(root);
            if (gitDir.isEmpty()) {
                return Optional.empty();
            }
            return readHead(gitDir.get());
        } catch (IOException | RuntimeException e) {
            LOG.debug("Could not determine the commit of the source folder: root={} message={}",
                    root, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolves the real git directory. A worktree or submodule has a {@code .git} <em>file</em> holding
     * a {@code gitdir:} pointer instead of a directory, and both are ordinary ways to check out source.
     */
    private static Optional<Path> resolveGitDir(Path root) throws IOException {
        Path candidate = root.resolve(GIT_DIR);
        if (Files.isDirectory(candidate)) {
            return Optional.of(candidate);
        }
        if (!Files.isRegularFile(candidate)) {
            return Optional.empty();
        }

        String content = Files.readString(candidate).strip();
        if (!content.startsWith(GITDIR_PREFIX)) {
            return Optional.empty();
        }

        Path pointer = Path.of(content.substring(GITDIR_PREFIX.length()).strip());
        Path resolved = pointer.isAbsolute() ? pointer : root.resolve(pointer).normalize();
        return Files.isDirectory(resolved) ? Optional.of(resolved) : Optional.empty();
    }

    private static Optional<String> readHead(Path gitDir) throws IOException {
        Path head = gitDir.resolve(HEAD_FILE);
        if (!Files.isRegularFile(head)) {
            return Optional.empty();
        }

        String content = Files.readString(head).strip();
        if (!content.startsWith(REF_PREFIX)) {
            // Detached HEAD: the file holds the object id itself.
            return objectId(content);
        }

        String ref = content.substring(REF_PREFIX.length()).strip();
        Optional<String> loose = readLooseRef(gitDir, ref);
        return loose.isPresent() ? loose : readPackedRef(gitDir, ref);
    }

    private static Optional<String> readLooseRef(Path gitDir, String ref) throws IOException {
        Path refFile = gitDir.resolve(ref);
        if (!refFile.normalize().startsWith(gitDir) || !Files.isRegularFile(refFile)) {
            return Optional.empty();
        }
        return objectId(Files.readString(refFile).strip());
    }

    /**
     * Falls back to {@code packed-refs}, where git moves refs it has garbage-collected. Peeled tag
     * lines (prefixed {@code ^}) are skipped — they name the tagged object, not the ref.
     */
    private static Optional<String> readPackedRef(Path gitDir, String ref) throws IOException {
        Path packed = gitDir.resolve(PACKED_REFS_FILE);
        if (!Files.isRegularFile(packed)) {
            return Optional.empty();
        }

        for (String line : Files.readAllLines(packed)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX) || trimmed.startsWith(PEELED_PREFIX)) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 2 && parts[1].equals(ref)) {
                return objectId(parts[0]);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> objectId(String value) {
        return value.length() == OBJECT_ID_LENGTH && value.chars().allMatch(LocalGitProbe::isHex)
                ? Optional.of(value)
                : Optional.empty();
    }

    private static boolean isHex(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
