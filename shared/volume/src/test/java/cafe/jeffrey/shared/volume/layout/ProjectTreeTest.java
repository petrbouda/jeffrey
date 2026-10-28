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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a project's directory holds as the layout reads it: each level is a directory named for
 * what it holds — no marker file says so — and a session's files carry their id, timestamp and
 * whether they are recordings.
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

    /** Lists like a volume whose listing carries no timestamps; a look at one entry still does. */
    private record UntimedListings(VolumeAccess delegate) implements VolumeAccess {

        @Override
        public List<VolumeEntry> list(VolumePath directory) {
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
            return delegate.read(file);
        }

        @Override
        public Optional<OpenedFile> open(VolumePath file) {
            return delegate.open(file);
        }
    }

    @Nested
    class WhatIsAProject {

        @Test
        void aDirectoryIdentifiedByItsName() {
            VolumeWriter.project(root, "project");

            ProjectTree tree = ProjectTree.at(volume, PROJECT).orElseThrow();

            assertEquals("project", tree.id());
            assertEquals(PROJECT, tree.path());
            assertTrue(tree.createdAt().isPresent(), "the directory's own time, as the volume gives it");
        }

        @Test
        void notAFileNorAnythingAbsent() throws IOException {
            Files.writeString(root.resolve("project"), "not a directory");

            assertTrue(ProjectTree.at(volume, PROJECT).isEmpty());
            assertTrue(ProjectTree.at(volume, VolumePath.root().resolve("gone")).isEmpty());
        }

        @Test
        void aWorkspaceListsItsVisibleDirectories() throws IOException {
            VolumeWriter.project(root, "orders");
            VolumeWriter.project(root, "billing");
            Files.createDirectories(root.resolve(".hidden"));
            Files.writeString(root.resolve("notes.txt"), "x");

            List<String> ids = ProjectTree.allIn(volume, VolumePath.root()).stream().map(ProjectTree::id).toList();

            assertEquals(List.of("billing", "orders"), ids);
        }
    }

    @Nested
    class InstancesAndSessions {

        private ProjectTree tree;
        private Path project;

        @BeforeEach
        void setUp() {
            project = VolumeWriter.project(root, "project");
            tree = ProjectTree.at(volume, PROJECT).orElseThrow();
        }

        @Test
        void everyVisibleDirectoryIsAnInstance() throws IOException {
            VolumeWriter.instance(project, "pod-b");
            VolumeWriter.instance(project, "pod-a");
            Files.createDirectories(project.resolve(".scratch"));

            assertEquals(List.of("pod-a", "pod-b"), tree.instances().stream().map(InstanceDirectory::id).toList());
            assertTrue(tree.instance("pod-b").isPresent());
            assertTrue(tree.instance("pod-c").isEmpty());
        }

        @Test
        void onlyADirectoryNamedForItsStartIsASession() throws IOException {
            Path instance = VolumeWriter.instance(project, "pod-a");
            Path session = VolumeWriter.session(instance, T0);
            Files.createDirectories(instance.resolve("not-a-session"));

            List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(PROJECT.resolve("pod-a"));

            assertEquals(List.of(session.getFileName().toString()),
                    oldestFirst.stream().map(SessionDirectory::id).toList());
            assertTrue(tree.session("not-a-session").isEmpty());
        }

        @Test
        void sessionsAreOrderedByTheStartTheirNamesCarry() {
            Path instance = VolumeWriter.instance(project, "pod-a");
            String first = VolumeWriter.session(instance, T0).getFileName().toString();
            String second = VolumeWriter.session(instance, T0.plusSeconds(60)).getFileName().toString();

            List<SessionDirectory> oldestFirst = tree.sessionsOldestFirst(PROJECT.resolve("pod-a"));

            assertEquals(List.of(first, second), oldestFirst.stream().map(SessionDirectory::id).toList());
            assertEquals(T0, oldestFirst.getFirst().createdAt());
            assertFalse(tree.isNewest(oldestFirst.getFirst()));
            assertTrue(tree.isNewest(oldestFirst.getLast()));
        }

        @Test
        void aSessionIsFoundInWhicheverInstanceHoldsIt() {
            VolumeWriter.instance(project, "pod-a");
            String id = VolumeWriter.session(VolumeWriter.instance(project, "pod-b"), T0).getFileName().toString();

            SessionDirectory session = tree.session(id).orElseThrow();

            assertEquals("pod-b", session.instanceId());
            assertEquals(T0, session.createdAt());
        }

        @Test
        void anIdThatIsNotOneDirectoryNamesNothing() {
            String id = VolumeWriter.session(VolumeWriter.instance(project, "pod-a"), T0).getFileName().toString();

            assertTrue(tree.session("../pod-a/" + id).isEmpty());
            assertTrue(tree.instance("../project").isEmpty());
            assertTrue(tree.instance("..").isEmpty());
        }
    }

    @Nested
    class SessionFiles {

        private static final byte[] CONTENT = "gc log line\n".repeat(100).getBytes(StandardCharsets.UTF_8);

        private ProjectTree tree;
        private Path session;
        private VolumePath sessionPath;

        @BeforeEach
        void setUp() {
            Path project = VolumeWriter.project(root, "project");
            session = VolumeWriter.session(VolumeWriter.instance(project, "pod-a"), T0);
            sessionPath = PROJECT.resolve("pod-a").resolve(session.getFileName().toString());
            tree = ProjectTree.at(volume, PROJECT).orElseThrow();
        }

        @Test
        void reportsAFileThatIsThere() throws IOException {
            Files.write(session.resolve("gc.jvm-log"), CONTENT);

            SessionFile file = tree.files(sessionPath).getFirst();

            assertEquals("gc.jvm-log", file.id());
            assertEquals("gc.jvm-log", file.name());
            assertEquals(CONTENT.length, file.size());
            assertFalse(file.recording());
            assertEquals(sessionPath.resolve("gc.jvm-log"), file.path());
        }

        @Test
        void aRecordingIsKnownByItsNameWithoutTheExtensionAndTimedByIt() {
            VolumeWriter.file(session, "rec-20260220-130500+0100.jfr.lz4", "chunk");

            SessionFile file = tree.files(sessionPath).getFirst();

            assertEquals("rec-20260220-130500+0100", file.id());
            assertTrue(file.recording());
            assertEquals(Instant.parse("2026-02-20T12:05:00Z"), file.createdAt());
        }

        @Test
        void listsVisibleFilesNewestFirstByName() throws IOException {
            VolumeWriter.recording(session, T0, ZoneOffset.UTC, "chunk");
            VolumeWriter.recording(session, T0.plusSeconds(900), ZoneOffset.UTC, "chunk");
            Files.createDirectories(session.resolve("nested"));

            List<String> names = tree.files(sessionPath).stream().map(SessionFile::name).toList();

            assertEquals(List.of("rec-20260220-121500+0000.jfr", "rec-20260220-120000+0000.jfr"), names,
                    "a nested directory is no file of the session");
        }

        /**
         * async-profiler deletes its cache file as soon as it flushes a chunk, so a name the
         * listing has just read can be gone before its size is asked for. The next listing will
         * not mention it either; failing here would take the instance page down with it.
         */
        @Test
        void leavesOutAFileThatWentAwayWhileTheSessionWasListed() throws IOException {
            VolumeWriter.file(session, "rec-20260912-121559+0000.jfr", "chunk");
            Files.createSymbolicLink(session.resolve("rec-20260912-121559+0000.jfr.1~"), session.resolve("flushed"));

            List<String> names = tree.files(sessionPath).stream().map(SessionFile::name).toList();

            assertEquals(List.of("rec-20260912-121559+0000.jfr"), names);
        }

        /**
         * Some volumes list without timestamps (an Azure Files NFS share). A chunk is timed by its
         * name anyway; anything else costs one look at that file alone, and one gone by then is left
         * out like any file that vanished while listed.
         */
        @Test
        void onAVolumeListingNoTimesAFileIsTimedByOneLookAtIt() throws IOException {
            VolumeWriter.file(session, "rec-20260220-130500+0000.jfr", "chunk");
            Path log = VolumeWriter.file(session, "gc.log", "line");
            ProjectTree untimed = ProjectTree.at(new UntimedListings(volume), PROJECT).orElseThrow();

            List<SessionFile> files = untimed.files(sessionPath);

            assertEquals(List.of("rec-20260220-130500+0000.jfr", "gc.log"),
                    files.stream().map(SessionFile::name).toList());
            assertEquals(Instant.parse("2026-02-20T13:05:00Z"), files.getFirst().createdAt());
            assertEquals(Files.readAttributes(log, BasicFileAttributes.class).creationTime().toInstant(),
                    files.getLast().createdAt());
        }

        @Test
        void aSessionDirectoryThatIsGoneHasNoFiles() {
            assertEquals(List.of(), tree.files(PROJECT.resolve("pod-a").resolve("gone")));
        }
    }
}
