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
import cafe.jeffrey.shared.volume.VolumeFixture;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.liveness.SessionLifecycle;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A project's sessions as the volume says they are when read: the one answer the hub and a
 * Microscope reading the storage directly both take, so neither can call a live session finished.
 */
class ProjectSessionsTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final Instant NOW = T0.plusSeconds(3600);

    @TempDir
    Path root;

    private Path project;
    private ProjectSessions sessions;

    @BeforeEach
    void setUp() {
        project = VolumeWriter.project(root, "p-1", "project", T0);
        FilesystemVolume volume = new FilesystemVolume(root);
        sessions = new ProjectSessions(
                ProjectTree.at(volume, VolumePath.root().resolve("project")).orElseThrow(),
                VolumeFixture.liveness(Clock.fixed(NOW, ZoneOffset.UTC), volume));
    }

    private Path session(String instanceId, String sessionId, Instant createdAt) {
        return VolumeWriter.session(VolumeWriter.instance(project, instanceId, T0), sessionId, createdAt, null);
    }

    @Nested
    class Listing {

        @Test
        void everySessionNewestFirstAcrossInstances() {
            VolumeWriter.finished(session("pod-a", "s-old", T0), T0.plusSeconds(60));
            VolumeWriter.heartbeat(session("pod-b", "s-new", T0.plusSeconds(120)), NOW);

            List<VolumeSession> all = sessions.all();

            assertEquals(List.of("s-new", "s-old"), all.stream().map(VolumeSession::id).toList());
            assertTrue(all.getFirst().lifecycle().isRecording());
            assertEquals(SessionLifecycle.finished(T0.plusSeconds(60)), all.getLast().lifecycle());
        }

        /**
         * Each instance is read on its own: a live session in one instance is not finished by a
         * newer session in another. The older live session once read as FINISHED and had the
         * chunk its profiler was writing compressed.
         */
        @Test
        void aNewerSessionInAnotherInstanceFinishesNothing() {
            VolumeWriter.heartbeat(session("pod-a", "s-a", T0), NOW);
            VolumeWriter.heartbeat(session("pod-b", "s-b", T0.plusSeconds(60)), NOW);

            assertTrue(sessions.all().stream().allMatch(session -> session.lifecycle().isRecording()));
        }

        @Test
        void aNewerSessionInTheSameInstanceFinishesTheOneBefore() {
            session("pod-a", "s-1", T0);
            VolumeWriter.heartbeat(session("pod-a", "s-2", T0.plusSeconds(300)), NOW);

            VolumeSession older = sessions.session("s-1").orElseThrow();

            assertEquals(SessionLifecycle.finished(T0.plusSeconds(300)), older.lifecycle());
        }

        @Test
        void carriesTheRetainedFlag() throws IOException {
            Path dir = session("pod-a", "s-1", T0);
            Files.createFile(dir.resolve(SessionDirectory.RETAINED_FLAG));

            assertTrue(sessions.session("s-1").orElseThrow().retained());
        }

        @Test
        void anUnknownSessionIsNone() {
            assertEquals(Optional.empty(), sessions.session("missing"));
        }
    }

    @Nested
    class Instances {

        @Test
        void withoutASessionIsPending() {
            VolumeWriter.instance(project, "pod-a", T0);

            InstanceSummary summary = sessions.instances().getFirst();

            assertEquals(InstanceState.PENDING, summary.state());
            assertEquals(0, summary.sessionCount());
            assertEquals(Optional.empty(), summary.finishedAt());
        }

        @Test
        void isActiveWhileItsNewestRecordsAndFinishedAtItsEndAfterwards() {
            VolumeWriter.finished(session("pod-a", "s-1", T0), T0.plusSeconds(60));
            Path live = session("pod-a", "s-2", T0.plusSeconds(120));
            VolumeWriter.heartbeat(live, NOW);

            assertEquals(InstanceState.ACTIVE, sessions.instances().getFirst().state());

            VolumeWriter.finished(live, NOW.minusSeconds(5));

            InstanceSummary summary = sessions.instances().getFirst();
            assertEquals(InstanceState.FINISHED, summary.state());
            assertEquals(Optional.of(NOW.minusSeconds(5)), summary.finishedAt());
            assertEquals(2, summary.sessionCount());
        }

        @Test
        void oneInstanceWithItsSessionsAgreesWithItsSummary() {
            VolumeWriter.finished(session("pod-a", "s-1", T0), T0.plusSeconds(60));
            VolumeWriter.heartbeat(session("pod-a", "s-2", T0.plusSeconds(120)), NOW);

            InstanceSessionList instance = sessions.instance("pod-a").orElseThrow();

            assertEquals(List.of("s-2", "s-1"), instance.sessions().stream().map(VolumeSession::id).toList());
            assertEquals(InstanceState.ACTIVE, instance.summary().state());
            assertEquals(2, instance.summary().sessionCount());
            assertFalse(sessions.instance("pod-b").isPresent());
        }
    }

    /**
     * A session is read off one listing of its directory, and one that cannot be listed is still
     * read, each answer asked of the volume instead.
     */
    @Nested
    class OneListing {

        /**
         * Fails the listing of one directory, records every read, and lists the directories named
         * in {@code listedTimes} with those creation times, the way an SMB share lists them.
         */
        private static final class ProbedVolume implements VolumeAccess {

            private final VolumeAccess delegate;
            private final VolumePath unlistable;
            private final Map<String, Instant> listedTimes = new HashMap<>();
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
                        .map(entry -> listedTimes.containsKey(entry.name())
                                ? new VolumeEntry(entry.path(), entry.kind(), entry.size(),
                                Optional.of(listedTimes.get(entry.name())))
                                : entry)
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
            return new ProjectSessions(tree, VolumeFixture.liveness(Clock.fixed(NOW, ZoneOffset.UTC), volume));
        }

        @Test
        void aSessionWithoutALivenessDirectoryReadsNoLivenessFile() {
            session("pod-a", "s-1", T0);

            VolumeSession read = probed(VolumePath.root()).session("s-1").orElseThrow();

            assertTrue(read.lifecycle().heartbeatMissing());
            assertTrue(volume.reads.stream().noneMatch(path -> path.toString().contains(".heartbeat")));
        }

        @Test
        void aSessionThatCannotBeListedIsStillReadFromItsFiles() throws IOException {
            Path dir = session("pod-a", "s-1", T0);
            VolumeWriter.heartbeat(dir, NOW);
            Files.createFile(dir.resolve(SessionDirectory.RETAINED_FLAG));

            VolumeSession read = probed(S_1).session("s-1").orElseThrow();

            assertTrue(read.lifecycle().isRecording(), "its heartbeat is read, not taken for absent");
            assertTrue(read.retained(), "its pin is looked at, not taken for unset");
            assertThrows(UncheckedIOException.class, () -> tree.files(read));
        }

        @Test
        void theNewestSessionDoesNotReadASiblingListedWellBeforeIt() {
            Instant olderStart = T0.minus(Duration.ofHours(2));
            session("pod-a", "s-1", olderStart);
            VolumeWriter.heartbeat(session("pod-a", "s-2", T0), NOW);
            ProjectSessions sessions = probed(VolumePath.root());
            volume.listedTimes.put("s-1", olderStart);
            volume.listedTimes.put("s-2", T0);

            VolumeSession newest = sessions.session("s-2").orElseThrow();

            assertTrue(newest.lifecycle().isRecording());
            assertTrue(volume.reads.stream().noneMatch(path -> path.toString().contains("s-1")),
                    "the listing says s-1 started well before, so its marker is not read");
        }

        @Test
        void aNewerSiblingStillFinishesTheOlderSession() {
            Instant olderStart = T0.minus(Duration.ofHours(2));
            session("pod-a", "s-1", olderStart);
            VolumeWriter.heartbeat(session("pod-a", "s-2", T0), NOW);
            ProjectSessions sessions = probed(VolumePath.root());
            volume.listedTimes.put("s-1", olderStart);
            volume.listedTimes.put("s-2", T0);

            VolumeSession older = sessions.session("s-1").orElseThrow();

            assertEquals(SessionLifecycle.finished(T0), older.lifecycle());
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