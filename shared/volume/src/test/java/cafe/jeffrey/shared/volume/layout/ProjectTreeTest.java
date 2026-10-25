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

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeFixture;
import cafe.jeffrey.shared.volume.VolumePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a project's directory holds as the layout reads it: each level is what its marker says it
 * is, and a session's files carry their id, timestamp and whether they are recordings.
 */
class ProjectTreeTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final VolumePath PROJECT = VolumePath.root().resolve("project");

    @TempDir
    Path root;

    private FilesystemVolume volume;

    @BeforeEach
    void setUp() {
        volume = new FilesystemVolume(root);
    }

    @Nested
    class WhatIsAProject {

        @Test
        void aDirectoryWithItsMarker() {
            VolumeFixture.project(root, "p-1", "project", T0);

            ProjectTree tree = ProjectTree.at(volume, PROJECT).orElseThrow();

            assertEquals("p-1", tree.marker().projectId());
            assertEquals(PROJECT, tree.path());
        }

        @Test
        void notADirectoryWithoutOne() throws IOException {
            Files.createDirectories(root.resolve("project"));

            assertTrue(ProjectTree.at(volume, PROJECT).isEmpty());
        }

        @Test
        void notOneWhoseMarkerNamesNoProject() throws IOException {
            Path dir = Files.createDirectories(root.resolve("project"));
            Files.writeString(dir.resolve(JeffreyLayout.PROJECT_INFO_FILE),
                    "{\"projectId\":\" \",\"projectName\":\"project\",\"createdAt\":0}");

            assertTrue(ProjectTree.at(volume, PROJECT).isEmpty());
        }
    }

    @Nested
    class InstancesAndSessions {

        private ProjectTree tree;
        private Path project;

        @BeforeEach
        void setUp() {
            project = VolumeFixture.project(root, "p-1", "project", T0);
            tree = ProjectTree.at(volume, PROJECT).orElseThrow();
        }

        @Test
        void aDirectoryWithoutItsMarkerIsNoInstance() throws IOException {
            VolumeFixture.instance(project, "pod-a", T0);
            Files.createDirectories(project.resolve("pod-b"));

            assertEquals(List.of("pod-a"), tree.instances().stream().map(InstanceDirectory::id).toList());
            assertTrue(tree.instance("pod-b").isEmpty());
            assertTrue(tree.instanceDirectory("pod-b").isPresent(), "its directory is still there to remove");
        }

        @Test
        void sessionsAreOrderedByTheirStartNotTheirName() {
            Path instance = VolumeFixture.instance(project, "pod-a", T0);
            VolumeFixture.session(instance, "b-first", T0, null);
            VolumeFixture.session(instance, "a-second", T0.plusSeconds(60), null);

            List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(PROJECT.resolve("pod-a"));

            assertEquals(List.of("b-first", "a-second"), oldestFirst.stream().map(SessionDirectory::id).toList());
            assertEquals(T0.plusSeconds(60), ProjectTree.nextStart(oldestFirst, oldestFirst.getFirst()));
            assertNull(ProjectTree.nextStart(oldestFirst, oldestFirst.getLast()));
        }

        @Test
        void aSessionIsFoundInWhicheverInstanceHoldsIt() {
            VolumeFixture.session(VolumeFixture.instance(project, "pod-b", T0), "s-1", T0, "Europe/Prague");

            SessionDirectory session = tree.session("s-1").orElseThrow();

            assertEquals("pod-b", session.instanceId());
            assertEquals(ZoneId.of("Europe/Prague"), session.zone());
        }

        @Test
        void anIdThatIsNotOneDirectoryNamesNothing() {
            VolumeFixture.session(VolumeFixture.instance(project, "pod-a", T0), "s-1", T0, null);

            assertTrue(tree.session("../pod-a/s-1").isEmpty());
            assertTrue(tree.instance("../project").isEmpty());
            assertTrue(tree.instanceDirectory("..").isEmpty());
        }

        @Test
        void aSessionIsRetainedByItsFlagFile() throws IOException {
            Path dir = VolumeFixture.session(VolumeFixture.instance(project, "pod-a", T0), "s-1", T0, null);
            SessionDirectory session = tree.session("s-1").orElseThrow();
            assertFalse(tree.isRetained(session));

            Files.createFile(dir.resolve(SessionDirectory.RETAINED_FLAG));

            assertTrue(tree.isRetained(session));
        }
    }

    @Nested
    class SessionFiles {

        private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);
        private static final VolumePath SESSION = PROJECT.resolve("pod-a").resolve("s-1");

        private ProjectTree tree;
        private Path session;

        @BeforeEach
        void setUp() {
            Path project = VolumeFixture.project(root, "p-1", "project", T0);
            session = VolumeFixture.session(VolumeFixture.instance(project, "pod-a", T0), "s-1", T0, null);
            tree = ProjectTree.at(volume, PROJECT).orElseThrow();
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Files.write(session.resolve("gc.jvm-log"), CONTENT);

            SessionFile file = tree.files(SESSION, ZoneOffset.UTC).getFirst();

            assertEquals("gc.jvm-log", file.id());
            assertEquals("gc.jvm-log", file.name());
            assertEquals(CONTENT.length, file.size());
            assertFalse(file.recording());
            assertEquals(SESSION.resolve("gc.jvm-log"), file.path());
        }

        @Test
        void aRecordingIsKnownByItsNameWithoutTheExtensionAndTimedByIt() {
            VolumeFixture.file(session, "profile-20260220-130500.jfr.lz4", "chunk");

            SessionFile file = tree.files(SESSION, ZoneId.of("Europe/Prague")).getFirst();

            assertEquals("profile-20260220-130500", file.id());
            assertTrue(file.recording());
            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), file.createdAt());
        }

        @Test
        void listsVisibleFilesNewestFirstByName() throws IOException {
            VolumeFixture.file(session, "profile-20260220-120000.jfr", "chunk");
            VolumeFixture.file(session, "profile-20260220-121500.jfr", "chunk");
            Files.createDirectories(session.resolve("nested"));

            List<String> names = tree.files(SESSION, ZoneOffset.UTC).stream().map(SessionFile::name).toList();

            assertEquals(List.of("profile-20260220-121500.jfr", "profile-20260220-120000.jfr"), names,
                    "the marker, the heartbeat directory and a nested directory are no files of the session");
        }

        /**
         * async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
         * listing has just read can be gone before its size is asked for. The next listing will
         * not mention it either; failing here would take the instance page down with it.
         */
        @Test
        void leavesOutAFileThatWentAwayWhileTheSessionWasListed() throws IOException {
            VolumeFixture.file(session, "profile-20260912-121559.jfr", "chunk");
            Files.createSymbolicLink(session.resolve("profile-20260912-121559.jfr.1~"), session.resolve("flushed"));

            List<String> names = tree.files(SESSION, ZoneOffset.UTC).stream().map(SessionFile::name).toList();

            assertEquals(List.of("profile-20260912-121559.jfr"), names);
        }

        @Test
        void aSessionDirectoryThatIsGoneHasNoFiles() {
            assertEquals(List.of(), tree.files(PROJECT.resolve("pod-a").resolve("gone"), ZoneOffset.UTC));
        }
    }
}
