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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The write-once files read once: the provisioner's markers and a session's finished marker, and
 * nothing else; each forgotten when a listing stops showing it, when Microscope changes it, and
 * after its safety bound.
 */
class RememberingVolumeTest {

    private static final Instant T0 = Instant.parse("2026-10-26T06:00:00Z");
    private static final VolumePath INSTANCE = VolumePath.root().resolve("ws").resolve("project").resolve("pod-1");
    private static final VolumePath SESSION = INSTANCE.resolve("s-1");
    private static final VolumePath SESSION_MARKER = SESSION.resolve(".session-info.json");
    private static final VolumePath FINISHED = SESSION.resolve(".heartbeat").resolve("finished");
    private static final VolumePath HEARTBEAT = SESSION.resolve(".heartbeat").resolve("heartbeat");

    @TempDir
    Path root;

    private final List<VolumePath> reads = new ArrayList<>();
    private MovableClock clock;
    private RememberingVolume volume;

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Records each read the remembering volume passes on. */
    private final class RecordingReads extends ForwardingVolume {

        private RecordingReads(WritableVolume delegate) {
            super(delegate);
        }

        @Override
        public Optional<byte[]> read(VolumePath file) {
            reads.add(file);
            return super.read(file);
        }
    }

    /** Passes everything on. */
    private static class ForwardingVolume implements WritableVolume {

        private final WritableVolume delegate;

        private ForwardingVolume(WritableVolume delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<VolumeEntry> list(VolumePath directory) {
            return delegate.list(directory);
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

        @Override
        public void createDirectories(VolumePath directory) {
            delegate.createDirectories(directory);
        }

        @Override
        public void write(VolumePath file, byte[] content) {
            delegate.write(file, content);
        }

        @Override
        public void delete(VolumePath path) {
            delegate.delete(path);
        }
    }

    @BeforeEach
    void setUp() {
        Path project = VolumeWriter.project(root.resolve("ws"), "p-1", "project", T0);
        Path session = VolumeWriter.session(VolumeWriter.instance(project, "pod-1", T0), "s-1", T0, null);
        VolumeWriter.finished(session, T0.plusSeconds(60));
        VolumeWriter.heartbeat(session, T0.plusSeconds(30));
        clock = new MovableClock(T0);
        volume = new RememberingVolume(new RecordingReads(new FilesystemVolume(root)), clock);
    }

    private long readsOf(VolumePath file) {
        return reads.stream().filter(file::equals).count();
    }

    @Nested
    class WhatIsRemembered {

        @Test
        void aMarkerIsReadOnce() {
            volume.read(SESSION_MARKER);
            volume.read(SESSION_MARKER);

            assertEquals(1, readsOf(SESSION_MARKER));
        }

        @Test
        void aWrittenFinishedMarkerIsReadOnce() {
            volume.read(FINISHED);
            volume.read(FINISHED);

            assertEquals(1, readsOf(FINISHED));
        }

        @Test
        void theHeartbeatIsReadEveryTime() {
            volume.read(HEARTBEAT);
            volume.read(HEARTBEAT);

            assertEquals(2, readsOf(HEARTBEAT));
        }

        @Test
        void aFileThatWasNotThereIsAskedForAgain() {
            VolumePath missing = INSTANCE.resolve("s-2").resolve(".session-info.json");

            volume.read(missing);
            volume.read(missing);

            assertEquals(2, readsOf(missing));
        }

        @Test
        void aRememberedMarkerReadsAsWritten() {
            String first = new String(volume.read(SESSION_MARKER).orElseThrow(), StandardCharsets.UTF_8);

            assertEquals(first, new String(volume.read(SESSION_MARKER).orElseThrow(), StandardCharsets.UTF_8));
        }
    }

    @Nested
    class WhenItIsForgotten {

        @Test
        void aListingThatNoLongerShowsTheSessionForgetsIt() throws Exception {
            volume.read(SESSION_MARKER);
            deleteTree(root.resolve("ws/project/pod-1/s-1"));

            volume.list(INSTANCE);

            assertFalse(volume.read(SESSION_MARKER).isPresent(), "the hub removed it; the memo is not taken for it");
            assertEquals(2, readsOf(SESSION_MARKER));
        }

        @Test
        void aListingThatStillShowsTheSessionKeepsIt() {
            volume.read(SESSION_MARKER);

            volume.list(INSTANCE);
            volume.read(SESSION_MARKER);

            assertEquals(1, readsOf(SESSION_MARKER));
        }

        @Test
        void microscopesOwnDeleteForgetsEverythingUnderIt() {
            volume.read(SESSION_MARKER);
            volume.read(FINISHED);

            volume.delete(SESSION);

            assertFalse(volume.read(SESSION_MARKER).isPresent());
            assertFalse(volume.read(FINISHED).isPresent());
        }

        @Test
        void microscopesOwnWriteForgetsTheFile() {
            volume.read(SESSION_MARKER);

            volume.write(SESSION_MARKER, "{\"sessionId\":\"s-1\",\"createdAt\":1}".getBytes(StandardCharsets.UTF_8));

            assertTrue(new String(volume.read(SESSION_MARKER).orElseThrow(), StandardCharsets.UTF_8)
                    .contains("\"createdAt\":1"));
        }

        @Test
        void itIsAskedForAgainAfterItsSafetyBound() {
            volume.read(SESSION_MARKER);

            clock.now = T0.plus(RememberingVolume.REMEMBERED_FOR).plusSeconds(1);
            volume.read(SESSION_MARKER);

            assertEquals(2, readsOf(SESSION_MARKER));
        }
    }

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(path);
            }
        }
    }
}
