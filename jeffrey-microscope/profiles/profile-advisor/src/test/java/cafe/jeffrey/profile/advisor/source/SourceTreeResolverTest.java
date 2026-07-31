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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceTreeResolverTest {

    private static final String RECORDING_ID = "recording-1";
    private static final String COMMIT = "1234567890abcdef1234567890abcdef12345678";

    private final SourceTreeResolver resolver = new SourceTreeResolver(
            new RecordingCommitResolver(new StubTagsRepository(Map.of())));

    @Nested
    @DisplayName("Path validation")
    class PathValidation {

        @Test
        @DisplayName("rejects a blank path with a message pointing at the settings page")
        void rejectsBlank() {
            Exception blank = assertThrows(RuntimeException.class, () -> resolver.resolve("  ", RECORDING_ID));
            assertTrue(blank.getMessage().contains("Source & Settings"));

            assertThrows(RuntimeException.class, () -> resolver.resolve(null, RECORDING_ID));
        }

        @Test
        @DisplayName("rejects a relative path rather than resolving it against the server's working directory")
        void rejectsRelative() {
            Exception e = assertThrows(RuntimeException.class, () -> resolver.resolve("src/main/java", RECORDING_ID));
            assertTrue(e.getMessage().contains("absolute"));
        }

        @Test
        @DisplayName("rejects a path that does not exist")
        void rejectsMissing(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("not-there");
            Exception e = assertThrows(
                    RuntimeException.class, () -> resolver.resolve(missing.toString(), RECORDING_ID));
            assertTrue(e.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("rejects a regular file")
        void rejectsFile(@TempDir Path tempDir) throws IOException {
            Path file = Files.createFile(tempDir.resolve("pom.xml"));
            Exception e = assertThrows(
                    RuntimeException.class, () -> resolver.resolve(file.toString(), RECORDING_ID));
            assertTrue(e.getMessage().contains("not a directory"));
        }

        @Test
        @DisplayName("canonicalizes the root so later containment checks compare symlink-free paths")
        void canonicalizes(@TempDir Path tempDir) throws IOException {
            Path real = Files.createDirectory(tempDir.resolve("project"));
            Path link = tempDir.resolve("link-to-project");
            Files.createSymbolicLink(link, real);

            SourceTree tree = resolver.resolve(link.toString(), RECORDING_ID);

            assertEquals(real.toRealPath(), tree.root());
        }
    }

    @Nested
    @DisplayName("Ownership")
    class Ownership {

        @Test
        @DisplayName("is not AutoCloseable, so no caller can be written that deletes the user's folder")
        void isNotAutoCloseable() {
            assertFalse(AutoCloseable.class.isAssignableFrom(SourceTree.class));
        }

        @Test
        @DisplayName("leaves the folder on disk after a resolved tree is discarded")
        void leavesFolderOnDisk(@TempDir Path tempDir) throws IOException {
            Path project = Files.createDirectory(tempDir.resolve("project"));
            Files.writeString(project.resolve("Main.java"), "class Main {}");

            resolver.resolve(project.toString(), RECORDING_ID);
            System.gc();

            assertTrue(Files.isDirectory(project));
            assertTrue(Files.isRegularFile(project.resolve("Main.java")));
        }
    }

    @Nested
    @DisplayName("Commit resolution")
    class CommitResolution {

        @Test
        @DisplayName("reports no commit for a folder that is not a git checkout")
        void noCommitWithoutGit(@TempDir Path tempDir) {
            SourceTree tree = resolver.resolve(tempDir.toString(), RECORDING_ID);

            assertNull(tree.resolvedRef());
            assertFalse(tree.hasResolvedRef());
            assertFalse(tree.refMismatch());
        }

        @Test
        @DisplayName("reads a detached HEAD directly")
        void readsDetachedHead(@TempDir Path tempDir) throws IOException {
            Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(gitDir.resolve("HEAD"), COMMIT + "\n");

            SourceTree tree = resolver.resolve(tempDir.toString(), RECORDING_ID);

            assertEquals(COMMIT, tree.resolvedRef());
        }

        @Test
        @DisplayName("follows HEAD to a loose ref")
        void followsLooseRef(@TempDir Path tempDir) throws IOException {
            Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/master\n");
            Path refs = Files.createDirectories(gitDir.resolve("refs").resolve("heads"));
            Files.writeString(refs.resolve("master"), COMMIT + "\n");

            SourceTree tree = resolver.resolve(tempDir.toString(), RECORDING_ID);

            assertEquals(COMMIT, tree.resolvedRef());
        }

        @Test
        @DisplayName("falls back to packed-refs when the loose ref was garbage-collected")
        void followsPackedRef(@TempDir Path tempDir) throws IOException {
            Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/master\n");
            Files.writeString(gitDir.resolve("packed-refs"), """
                    # pack-refs with: peeled fully-peeled sorted
                    %s refs/heads/master
                    """.formatted(COMMIT));

            SourceTree tree = resolver.resolve(tempDir.toString(), RECORDING_ID);

            assertEquals(COMMIT, tree.resolvedRef());
        }

        @Test
        @DisplayName("flags a mismatch when the recording came from a different commit")
        void flagsMismatch(@TempDir Path tempDir) throws IOException {
            Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(gitDir.resolve("HEAD"), COMMIT + "\n");

            SourceTreeResolver tagged = new SourceTreeResolver(new RecordingCommitResolver(
                    new StubTagsRepository(Map.of("git.commit", "fedcba9876543210"))));

            assertTrue(tagged.resolve(tempDir.toString(), RECORDING_ID).refMismatch());
        }

        @Test
        @DisplayName("accepts an abbreviated recording tag that prefixes the checked-out commit")
        void acceptsAbbreviatedRef(@TempDir Path tempDir) throws IOException {
            Path gitDir = Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(gitDir.resolve("HEAD"), COMMIT + "\n");

            SourceTreeResolver tagged = new SourceTreeResolver(new RecordingCommitResolver(
                    new StubTagsRepository(Map.of("git.commit", COMMIT.substring(0, 8)))));

            assertFalse(tagged.resolve(tempDir.toString(), RECORDING_ID).refMismatch());
        }
    }

    private record StubTagsRepository(Map<String, String> tags) implements RecordingTagsRepository {

        @Override
        public void insert(String recordingId, Map<String, String> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingTag> listForRecording(String recordingId) {
            return tags.entrySet().stream()
                    .map(entry -> new RecordingTag(entry.getKey(), entry.getValue()))
                    .toList();
        }

        @Override
        public Map<String, List<RecordingTag>> listForRecordings(Collection<String> recordingIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteForRecording(String recordingId) {
            throw new UnsupportedOperationException();
        }
    }
}
