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
        project = VolumeWriter.project(root, "project");
        FilesystemVolume volume = new FilesystemVolume(root);
        sessions = new ProjectSessions(ProjectTree.at(volume, VolumePath.root().resolve("project")).orElseThrow());
    }

    /** A session started at {@code start} in the instance; its id. */
    private String session(String instanceId, Instant start) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId), start).getFileName().toString();
    }

    @Nested
    class Listing {

        @Test
        void everySessionNewestFirstAcrossInstances() {
            String old = session("pod-a", T0);
            String recent = session("pod-b", T0.plusSeconds(120));

            List<VolumeSession> all = sessions.all();

            assertEquals(List.of(recent, old), all.stream().map(VolumeSession::id).toList());
        }

        /**
         * Each instance is read on its own: a newer session in one instance does not end a session
         * in another. The older live session once read as FINISHED and had the chunk its profiler
         * was writing compressed.
         */
        @Test
        void eachInstanceHasItsOwnNewestSession() {
            session("pod-a", T0);
            session("pod-b", T0.plusSeconds(60));

            assertTrue(sessions.all().stream().allMatch(VolumeSession::newest));
        }

        @Test
        void aNewerSessionInTheSameInstanceEndsTheOneBefore() {
            String first = session("pod-a", T0);
            String second = session("pod-a", T0.plusSeconds(300));

            assertFalse(sessions.session(first).orElseThrow().newest());
            assertTrue(sessions.session(second).orElseThrow().newest());
            assertEquals(List.of(true, false), sessions.all().stream().map(VolumeSession::newest).toList());
        }

        @Test
        void anUnknownSessionIsNone() {
            assertEquals(Optional.empty(), sessions.session("missing"));
            assertEquals(Optional.empty(), sessions.session(VolumeWriter.sessionIdAt(T0)));
        }

        @Test
        void theNewestOfEachInstanceAreReadNewestFirst() {
            String a1 = session("pod-a", T0);
            String a2 = session("pod-a", T0.plusSeconds(60));
            String a3 = session("pod-a", T0.plusSeconds(120));
            String b1 = session("pod-b", T0.plusSeconds(30));

            List<String> newest = sessions.newestOfEachInstance(2).stream().map(VolumeSession::id).toList();

            assertEquals(List.of(a3, a2, b1), newest);
            assertFalse(newest.contains(a1));
        }
    }

    @Nested
    class Instances {

        @Test
        void withoutASessionHasNoNewest() {
            VolumeWriter.instance(project, "pod-a");

            InstanceSummary summary = sessions.instances().getFirst();

            assertEquals(0, summary.sessionCount());
            assertEquals(Optional.empty(), summary.newest());
        }

        @Test
        void isSummedUpByItsNewestSession() {
            session("pod-a", T0);
            String second = session("pod-a", T0.plusSeconds(120));

            InstanceSummary summary = sessions.instances().getFirst();

            assertEquals(second, summary.newest().orElseThrow().id());
            assertTrue(summary.newest().orElseThrow().newest());
            assertEquals(2, summary.sessionCount());
        }

        @Test
        void oneInstanceNamesAPageOfItsSessionsNewestFirst() {
            String first = session("pod-a", T0);
            String second = session("pod-a", T0.plusSeconds(120));

            InstanceSessionList instance = sessions.instance("pod-a", SessionPage.ALL).orElseThrow();

            assertEquals(List.of(second, first), instance.sessions().stream().map(SessionHeader::id).toList());
            assertEquals(List.of(true, false), instance.sessions().stream().map(SessionHeader::newest).toList());
            assertFalse(instance.hasOlder());
            assertFalse(sessions.instance("pod-b", SessionPage.ALL).isPresent());
        }

        @Test
        void aPageAfterTheCursorKnowsWhetherOlderOnesAreLeft() {
            session("pod-a", T0);
            String second = session("pod-a", T0.plusSeconds(60));
            String third = session("pod-a", T0.plusSeconds(120));

            InstanceSessionList page = sessions.instance("pod-a", new SessionPage(1, Optional.of(third))).orElseThrow();

            assertEquals(List.of(second), page.sessions().stream().map(SessionHeader::id).toList());
            assertFalse(page.sessions().getFirst().newest());
            assertTrue(page.hasOlder());
        }
    }

    /**
     * Nothing below a workspace is a file to read: a session is one listing of its directory, and
     * one that cannot be listed is still read; whether it is the newest is its instance's listing.
     */
    @Nested
    class OneListing {

        /** Fails the listing of one directory, records every read and listing, and lists no times, as an NFS share lists. */
        private static final class ProbedVolume implements VolumeAccess {

            private final VolumeAccess delegate;
            private final VolumePath unlistable;
            private final List<VolumePath> reads = new ArrayList<>();
            private final List<VolumePath> listings = new ArrayList<>();

            private ProbedVolume(VolumeAccess delegate, VolumePath unlistable) {
                this.delegate = delegate;
                this.unlistable = unlistable;
            }

            @Override
            public List<VolumeEntry> list(VolumePath directory) {
                listings.add(directory);
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

        private ProbedVolume volume;
        private ProjectTree tree;

        private ProjectSessions probed(VolumePath unlistable) {
            volume = new ProbedVolume(new FilesystemVolume(root), unlistable);
            tree = ProjectTree.at(volume, VolumePath.root().resolve("project")).orElseThrow();
            return new ProjectSessions(tree);
        }

        private static VolumePath pathOf(String instanceId, String sessionId) {
            return VolumePath.root().resolve("project").resolve(instanceId).resolve(sessionId);
        }

        @Test
        void aSessionThatCannotBeListedIsStillRead() {
            String id = session("pod-a", T0);

            VolumeSession read = probed(pathOf("pod-a", id)).session(id).orElseThrow();

            assertTrue(read.newest());
            assertThrows(UncheckedIOException.class, () -> tree.files(read));
        }

        @Test
        void noFileIsReadToKnowTheSessionsOrWhichIsNewest() {
            String older = session("pod-a", T0.minus(Duration.ofHours(2)));
            String newest = session("pod-a", T0);

            ProjectSessions probed = probed(VolumePath.root());

            assertTrue(probed.session(newest).orElseThrow().newest());
            assertFalse(probed.session(older).orElseThrow().newest());
            assertEquals(2, probed.instances().getFirst().sessionCount());
            assertEquals(List.of(), volume.reads, "names say it all");
        }

        @Test
        void aDirectoryNotNamedForItsStartIsNoSession() throws IOException {
            String first = session("pod-a", T0.minus(Duration.ofHours(4)));
            Files.createDirectories(project.resolve("pod-a").resolve("scratch"));

            assertTrue(probed(VolumePath.root()).session(first).orElseThrow().newest());
            assertEquals(1, sessions.instances().getFirst().sessionCount());
        }

        @Test
        void theInstancePageListsOnlyTheInstance() {
            session("pod-a", T0);
            session("pod-a", T0.plusSeconds(60));

            probed(VolumePath.root()).instance("pod-a", SessionPage.ALL).orElseThrow();

            assertEquals(List.of(VolumePath.root().resolve("project").resolve("pod-a")), volume.listings);
        }

        @Test
        void itsFilesComeFromTheListingItWasReadFrom() {
            String id = session("pod-a", T0);
            Path dir = project.resolve("pod-a").resolve(id);
            VolumeWriter.file(dir, "profile-20260220-120000.UTC.jfr", "x");

            ProjectSessions sessions = probed(VolumePath.root());
            VolumeSession read = sessions.session(id).orElseThrow();
            VolumeWriter.file(dir, "profile-20260220-121000.UTC.jfr", "x");

            assertEquals(List.of("profile-20260220-120000.UTC.jfr"),
                    tree.files(read).stream().map(SessionFile::name).toList());
        }
    }
}
