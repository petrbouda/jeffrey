/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.volume.layout;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A project's sessions as the volume says they are when read: the one answer the hub and a
 * Microscope reading the storage directly both take, so neither can take a session for its
 * instance's newest when a newer one has started.
 */
class ProjectSessionsTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    @TempDir
    Path root;

    private Path project;
    private ProjectSessions sessions;

    @BeforeEach
    void setUp() {
        project = VolumeWriter.project(root, "p-1", "project", T0);
        FilesystemVolume volume = new FilesystemVolume(root);
        sessions = new ProjectSessions(ProjectTree.at(volume, VolumePath.root().resolve("project")).orElseThrow());
    }

    private Path session(String instanceId, String sessionId, Instant createdAt) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId, T0), sessionId, createdAt, null);
    }

    @Nested
    class Listing {

        @Test
        void everySessionNewestFirstAcrossInstances() {
            session("pod-a", "s-old", T0);
            session("pod-b", "s-new", T0.plusSeconds(120));

            List<VolumeSession> all = sessions.all();

            assertEquals(List.of("s-new", "s-old"), all.stream().map(VolumeSession::id).toList());
        }

        /**
         * Each instance is read on its own: a newer session in one instance does not end a session
         * in another. The older live session once read as FINISHED and had the chunk its profiler
         * was writing compressed.
         */
        @Test
        void eachInstanceHasItsOwnNewestSession() {
            session("pod-a", "s-a", T0);
            session("pod-b", "s-b", T0.plusSeconds(60));

            assertTrue(sessions.all().stream().allMatch(VolumeSession::newest));
        }

        @Test
        void aNewerSessionInTheSameInstanceEndsTheOneBefore() {
            session("pod-a", "s-1", T0);
            session("pod-a", "s-2", T0.plusSeconds(300));

            assertFalse(sessions.session("s-1").orElseThrow().newest());
            assertTrue(sessions.session("s-2").orElseThrow().newest());
            assertEquals(List.of(true, false), sessions.all().stream().map(VolumeSession::newest).toList());
        }

        @Test
        void anUnknownSessionIsNone() {
            assertEquals(Optional.empty(), sessions.session("missing"));
        }
    }

    @Nested
    class Instances {

        @Test
        void withoutASessionHasNoNewest() {
            VolumeWriter.instance(project, "pod-a", T0);

            InstanceSummary summary = sessions.instances().getFirst();

            assertEquals(0, summary.sessionCount());
            assertEquals(Optional.empty(), summary.newest());
        }

        @Test
        void isSummedUpByItsNewestSession() {
            session("pod-a", "s-1", T0);
            session("pod-a", "s-2", T0.plusSeconds(120));

            InstanceSummary summary = sessions.instances().getFirst();

            assertEquals("s-2", summary.newest().orElseThrow().id());
            assertTrue(summary.newest().orElseThrow().newest());
            assertEquals(2, summary.sessionCount());
        }

        @Test
        void oneInstanceWithItsSessionsAgreesWithItsSummary() {
            session("pod-a", "s-1", T0);
            session("pod-a", "s-2", T0.plusSeconds(120));

            InstanceSessionList instance = sessions.instance("pod-a").orElseThrow();

            assertEquals(List.of("s-2", "s-1"), instance.sessions().stream().map(VolumeSession::id).toList());
            assertEquals(List.of(true, false), instance.sessions().stream().map(VolumeSession::newest).toList());
            assertEquals("s-2", instance.summary().newest().orElseThrow().id());
            assertEquals(2, instance.summary().sessionCount());
            assertFalse(sessions.instance("pod-b").isPresent());
        }

        @Test
        void aPageAfterTheCursorStillKnowsTheInstancesNewest() {
            String first = VolumeWriter.sessionIdAt(T0);
            String second = VolumeWriter.sessionIdAt(T0.plusSeconds(60));
            String third = VolumeWriter.sessionIdAt(T0.plusSeconds(120));
            session("pod-a", first, T0);
            session("pod-a", second, T0.plusSeconds(60));
            session("pod-a", third, T0.plusSeconds(120));

            InstanceSessionList page = sessions.instance("pod-a", new SessionPage(1, Optional.of(third))).orElseThrow();

            assertEquals(List.of(second), page.sessions().stream().map(VolumeSession::id).toList());
            assertFalse(page.sessions().getFirst().newest());
            assertEquals(third, page.summary().newest().orElseThrow().id());
            assertTrue(page.hasOlder());
        }
    }

    /**
     * A session is read off one listing of its directory, and one that cannot be listed is still
     * read; its siblings are read only as far as their names say they may have started after it.
     */
    @Nested
    class OneListing {

        /** Fails the listing of one directory, records every read, and lists no creation times, as an NFS share lists. */
        private static final class ProbedVolume implements VolumeAccess {

            private final VolumeAccess delegate;
            private final VolumePath unlistable;
            private final List<VolumePath> reads = new ArrayList<>();

            private ProbedVolume(VolumeAccess delegate, VolumePath unlistable) {
                this.delegate = delegate;
                this.unlistable = unlistable;
            }

            @Override
            public List<VolumeEntry> list(VolumePath directory) {
                if (directory.equals(unlistable)) {
                    throw new UncheckedIOException(new IOException("stale handle"));
                }
                return delegate.list(directory).stream()
                        .map(entry -> new VolumeEntry(entry.path(), entry.kind(), entry.size(), Optional.empty()))
                        .toList();
            }

            @Override
            public Optional<VolumeEntry> stat(VolumePath path) {
                return delegate.stat(path);
            }

            @Override
            public Optional<byte[]> read(VolumePath file) {
                reads.add(file);
                return delegate.read(file);
            }

            @Override
            public Optional<OpenedFile> open(VolumePath file) {
                return delegate.open(file);
            }
        }

        private static final VolumePath S_1 = VolumePath.root().resolve("project").resolve("pod-a").resolve("s-1");

        private ProbedVolume volume;
        private ProjectTree tree;

        private ProjectSessions probed(VolumePath unlistable) {
            volume = new ProbedVolume(new FilesystemVolume(root), unlistable);
            tree = ProjectTree.at(volume, VolumePath.root().resolve("project")).orElseThrow();
            return new ProjectSessions(tree);
        }

        @Test
        void aSessionThatCannotBeListedIsStillRead() {
            session("pod-a", "s-1", T0);

            VolumeSession read = probed(S_1).session("s-1").orElseThrow();

            assertTrue(read.newest());
            assertThrows(UncheckedIOException.class, () -> tree.files(read));
        }

        @Test
        void theNewestSessionDoesNotReadASiblingNamedBeforeIt() {
            String older = VolumeWriter.sessionIdAt(T0.minus(Duration.ofHours(2)));
            String newest = VolumeWriter.sessionIdAt(T0);
            session("pod-a", older, T0.minus(Duration.ofHours(2)));
            session("pod-a", newest, T0);

            VolumeSession read = probed(VolumePath.root()).session(newest).orElseThrow();

            assertTrue(read.newest());
            assertTrue(volume.reads.stream().noneMatch(path -> path.toString().contains(older)),
                    "its name says the sibling started before, so its marker is not read");
        }

        @Test
        void aSiblingNamedAfterItEndsTheOlderSession() {
            String older = VolumeWriter.sessionIdAt(T0.minus(Duration.ofHours(2)));
            String newer = VolumeWriter.sessionIdAt(T0);
            session("pod-a", older, T0.minus(Duration.ofHours(2)));
            session("pod-a", newer, T0);

            assertFalse(probed(VolumePath.root()).session(older).orElseThrow().newest());
        }

        @Test
        void onlyTheNextSessionAlongIsRead() {
            String first = VolumeWriter.sessionIdAt(T0.minus(Duration.ofHours(4)));
            String second = VolumeWriter.sessionIdAt(T0.minus(Duration.ofHours(2)));
            String third = VolumeWriter.sessionIdAt(T0);
            session("pod-a", first, T0.minus(Duration.ofHours(4)));
            session("pod-a", second, T0.minus(Duration.ofHours(2)));
            session("pod-a", third, T0);

            VolumeSession read = probed(VolumePath.root()).session(first).orElseThrow();

            assertFalse(read.newest());
            assertTrue(volume.reads.stream().noneMatch(path -> path.toString().contains(third)),
                    "the second session settles it, so the third's marker is not read");
        }

        @Test
        void aSiblingWhoseMarkerIsNotWrittenYetIsNoNewerSession() throws IOException {
            String first = VolumeWriter.sessionIdAt(T0.minus(Duration.ofHours(4)));
            String undescribed = VolumeWriter.sessionIdAt(T0);
            session("pod-a", first, T0.minus(Duration.ofHours(4)));
            Files.createDirectories(project.resolve("pod-a").resolve(undescribed));

            assertTrue(probed(VolumePath.root()).session(first).orElseThrow().newest(),
                    "a directory without its marker is no session yet");
        }

        @Test
        void itsFilesComeFromTheListingItWasReadFrom() {
            Path dir = session("pod-a", "s-1", T0);
            VolumeWriter.file(dir, "profile-20260220-120000.jfr", "x");

            ProjectSessions sessions = probed(VolumePath.root());
            VolumeSession read = sessions.session("s-1").orElseThrow();
            VolumeWriter.file(dir, "profile-20260220-121000.jfr", "x");

            assertEquals(List.of("profile-20260220-120000.jfr"),
                    tree.files(read).stream().map(SessionFile::name).toList());
        }
    }
}
