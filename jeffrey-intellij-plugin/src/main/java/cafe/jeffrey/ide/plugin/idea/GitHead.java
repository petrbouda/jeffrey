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

package cafe.jeffrey.ide.plugin.idea;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The checkout a project window is sitting on, read straight from {@code .git} rather than through
 * the Git plugin.
 *
 * <p>Microscope needs this to answer one question: is the code in this window the code that was
 * profiled? A recording carries the commit it was built from ({@code profiles_get.recordingCommit}),
 * and a branch and a HEAD commit here are what that value gets compared against. Without them a
 * reader mapping a frame to a file is trusting that nobody moved in between.
 *
 * <p>Deliberately dependency-free, for the same reason {@code KotlinResolver} sticks to the Java PSI:
 * a hard dependency on Git4Idea would make the plugin refuse to load in an IDE that has version
 * control switched off, and all that is wanted here are two strings. Anything unreadable — no
 * repository, a format this does not know, an I/O error — is reported as absent, never as a guess.
 */
final class GitHead {

    private static final Logger LOG = Logger.getInstance(GitHead.class);

    private static final String GIT_DIR = ".git";
    private static final String HEAD_FILE = "HEAD";
    private static final String PACKED_REFS_FILE = "packed-refs";
    private static final String GITDIR_PREFIX = "gitdir:";
    private static final String REF_PREFIX = "ref:";
    private static final String HEADS_PREFIX = "refs/heads/";
    private static final String COMMENT_PREFIX = "#";
    private static final String PEELED_PREFIX = "^";
    private static final char PACKED_REF_SEPARATOR = ' ';

    /** A 40-character SHA-1, which is what a detached HEAD and a ref file both hold. */
    private static final int SHA1_LENGTH = 40;

    private GitHead() {
    }

    /**
     * The branch and commit of the checkout at {@code basePath}, or {@link Checkout#UNKNOWN} when
     * there is nothing readable to report.
     */
    static Checkout read(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return Checkout.UNKNOWN;
        }
        try {
            Path gitDir = gitDir(Path.of(basePath));
            if (gitDir == null) {
                return Checkout.UNKNOWN;
            }
            String head = firstLine(gitDir.resolve(HEAD_FILE));
            if (head == null) {
                return Checkout.UNKNOWN;
            }
            // Detached HEAD: the file holds the commit itself and there is no branch to name.
            if (!head.startsWith(REF_PREFIX)) {
                return new Checkout(null, sha(head));
            }
            String ref = head.substring(REF_PREFIX.length()).trim();
            return new Checkout(branchName(ref), commitOf(gitDir, ref));
        } catch (Exception e) {
            LOG.debug("Cannot read the checkout of a project: base_path=" + basePath, e);
            return Checkout.UNKNOWN;
        }
    }

    /**
     * The real git directory. Usually {@code <project>/.git}, but a worktree or a submodule leaves a
     * {@code .git} <em>file</em> pointing at the directory the refs actually live in.
     */
    private static Path gitDir(Path projectDir) throws IOException {
        Path candidate = projectDir.resolve(GIT_DIR);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        if (!Files.isRegularFile(candidate)) {
            return null;
        }
        String pointer = firstLine(candidate);
        if (pointer == null || !pointer.startsWith(GITDIR_PREFIX)) {
            return null;
        }
        Path target = Path.of(pointer.substring(GITDIR_PREFIX.length()).trim());
        Path resolved = target.isAbsolute() ? target : projectDir.resolve(target).normalize();
        return Files.isDirectory(resolved) ? resolved : null;
    }

    /**
     * The commit a ref points at: its own file when the ref is loose, otherwise the entry
     * {@code git pack-refs} left in {@code packed-refs}. A branch that has never been packed and has
     * no loose file does not exist, and is reported as absent.
     */
    private static String commitOf(Path gitDir, String ref) throws IOException {
        String loose = firstLine(gitDir.resolve(ref));
        if (loose != null) {
            return sha(loose);
        }
        return packedRef(gitDir, ref);
    }

    private static String packedRef(Path gitDir, String ref) throws IOException {
        Path packed = gitDir.resolve(PACKED_REFS_FILE);
        if (!Files.isRegularFile(packed)) {
            return null;
        }
        List<String> lines = Files.readAllLines(packed, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank() || line.startsWith(COMMENT_PREFIX) || line.startsWith(PEELED_PREFIX)) {
                continue;
            }
            int separator = line.indexOf(PACKED_REF_SEPARATOR);
            if (separator > 0 && ref.equals(line.substring(separator + 1).trim())) {
                return sha(line.substring(0, separator));
            }
        }
        return null;
    }

    /** {@code refs/heads/main} is the branch {@code main}; anything else is reported as it is. */
    private static String branchName(String ref) {
        if (ref.startsWith(HEADS_PREFIX)) {
            return ref.substring(HEADS_PREFIX.length());
        }
        return ref;
    }

    /**
     * A commit id, or null when the text is not one. Checked rather than trusted: a truncated or
     * half-written ref file would otherwise be reported as a commit that no repository contains.
     */
    private static String sha(String text) {
        String trimmed = text.trim();
        if (trimmed.length() != SHA1_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return null;
            }
        }
        return trimmed;
    }

    private static String firstLine(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return null;
        }
        return lines.getFirst().trim();
    }

    /**
     * @param branch the checked-out branch, or null when HEAD is detached or unreadable
     * @param commit the commit HEAD points at, or null when unreadable
     */
    record Checkout(String branch, String commit) {

        static final Checkout UNKNOWN = new Checkout(null, null);
    }
}
