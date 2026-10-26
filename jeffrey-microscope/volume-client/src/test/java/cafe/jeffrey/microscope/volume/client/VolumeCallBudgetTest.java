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

import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.liveness.SessionDeadlines;
import cafe.jeffrey.shared.volume.liveness.SessionLiveness;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many questions each read asks of the volume. On an Azure file share every one is an HTTPS
 * round-trip made while a page waits, so the counts are pinned: a change that asks again for what
 * a listing already answered fails here, not on a slow page.
 */
class VolumeCallBudgetTest {

    private static final Instant T0 = Instant.parse("2026-10-26T06:00:00Z");
    /** After the newest session started; sessions start two hours apart, clear of the directory-order margin. */
    private static final Instant NOW = T0.plus(Duration.ofHours(24));
    private static final SessionDeadlines DEADLINES =
            new SessionDeadlines(Duration.ofSeconds(10), Duration.ofMinutes(1));
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");
    private static final int SESSIONS = 12;
    private static final int CHUNKS_PER_SESSION = 20;
    private static final String SESSION_PREFIX = "session-";
    private static final String LIVE_SESSION = SESSION_PREFIX + (SESSIONS - 1);
    private static final String FINISHED_SESSION = SESSION_PREFIX + 0;

    @TempDir
    Path root;

    private CountingVolume volume;
    private RemoteClients clients;

    /**
     * Records every question asked of the volume it wraps, and lists each session directory with
     * the time it was created the way the share says it: when the session started, as an SMB share
     * lists it, or no time at all, as an NFS share does.
     */
    private static final class CountingVolume implements VolumeAccess {

        private final VolumeAccess delegate;
        private final boolean listsTimes;
        private final List<String> calls = new ArrayList<>();

        private CountingVolume(VolumeAccess delegate, boolean listsTimes) {
            this.delegate = delegate;
            this.listsTimes = listsTimes;
        }

        @Override
        public List<VolumeEntry> list(VolumePath directory) {
            calls.add("list " + directory);
            return delegate.list(directory).stream()
                    .map(this::asTheShareListsIt)
                    .toList();
        }

        private VolumeEntry asTheShareListsIt(VolumeEntry entry) {
            if (!entry.isDirectory() || !entry.name().startsWith(SESSION_PREFIX)) {
                return entry;
            }
            Optional<Instant> createdAt = listsTimes
                    ? Optional.of(startOf(Integer.parseInt(entry.name().substring(SESSION_PREFIX.length()))))
                    : Optional.empty();
            return new VolumeEntry(entry.path(), entry.kind(), entry.size(), createdAt);
        }

        @Override
        public Optional<VolumeEntry> stat(VolumePath path) {
            calls.add("stat " + path);
            return delegate.stat(path);
        }

        @Override
        public Optional<byte[]> read(VolumePath file) {
            calls.add("read " + file);
            return delegate.read(file);
        }

        @Override
        public Optional<OpenedFile> open(VolumePath file) {
            calls.add("open " + file);
            return delegate.open(file);
        }

        private int countAndReset() {
            int count = calls.size();
            calls.clear();
            return count;
        }
    }

    /**
     * One instance with twelve sessions two hours apart of twenty chunks and a log each: eleven finished
     * cleanly, the newest still beating.
     */
    @BeforeEach
    void setUp() throws IOException {
        Path project = VolumeWriter.project(Files.createDirectories(root.resolve("prod")), "p-orders", "orders", T0);
        Path instance = VolumeWriter.instance(project, "pod-1", T0);
        for (int s = 0; s < SESSIONS; s++) {
            Instant start = startOf(s);
            Path session = VolumeWriter.session(instance, SESSION_PREFIX + s, start, null);
            for (int c = 0; c < CHUNKS_PER_SESSION; c++) {
                VolumeWriter.file(session, "profile-%02d%02d.jfr.lz4".formatted(s, c), "x");
            }
            VolumeWriter.file(session, "jvm.log", "x");
            if (s < SESSIONS - 1) {
                VolumeWriter.finished(session, start.plus(Duration.ofMinutes(50)));
            } else {
                VolumeWriter.heartbeat(session, NOW);
            }
        }
        clients = clientsOver(true);
    }

    private static Instant startOf(int session) {
        return T0.plus(Duration.ofHours(2L * session));
    }

    private RemoteClients clientsOver(boolean listsTimes) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        volume = new CountingVolume(new FilesystemVolume(root), listsTimes);
        return VolumeRemoteClients.of(new VolumeSource(
                volume, new SessionLiveness(clock, volume, DEADLINES), clock, "budget", true));
    }

    @Test
    void anInstanceWithItsSessionsCostsThreeQuestionsASession() {
        clients.instances().instance(ORDERS, "pod-1");

        // workspace listing + project marker, instance marker, instance listing; then per session its
        // marker, its listing and its finished marker; the live one's heartbeat
        assertEquals(4 + 3 * SESSIONS + 1, volume.countAndReset());
    }

    @Test
    void theProjectsInstancesReadOnlyTheNewestSessionsLiveness() {
        clients.instances().projectInstances(ORDERS);

        // workspace listing + project marker, project listing, instance marker, instance listing, the
        // session markers; the newest session's listing, finished marker and heartbeat
        assertEquals(5 + SESSIONS + 3, volume.countAndReset());
    }

    @Test
    void everySessionOfTheProjectCostsThreeQuestionsASession() {
        clients.repository().recordingSessions(ORDERS, RecordingSessionFilter.ALL);

        // as one instance, plus the project listing
        assertEquals(5 + 3 * SESSIONS + 1, volume.countAndReset());
    }

    @Test
    void aCleanlyFinishedSessionNeverReadsItsSiblings() {
        clients.repository().recordingSession(ORDERS, FINISHED_SESSION);

        // workspace listing + project marker, project listing, its marker, its instance's marker,
        // its listing and its finished marker
        assertEquals(7, volume.countAndReset());
    }

    @Test
    void theLiveSessionKnowsItIsTheNewestFromTheInstanceListing() {
        clients.repository().recordingSession(ORDERS, LIVE_SESSION);

        // as a finished one, plus its heartbeat and the instance listing, whose times say no
        // sibling started after it, so no sibling's marker is read
        assertEquals(7 + 1 + 1, volume.countAndReset());
    }

    @Test
    void withoutListedTimesTheLiveSessionReadsEverySibling() {
        clientsOver(false).repository().recordingSession(ORDERS, LIVE_SESSION);

        // an NFS share lists no times, so every other session's marker is read for its start
        assertEquals(7 + 1 + 1 + (SESSIONS - 1), volume.countAndReset());
    }
}
