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
import cafe.jeffrey.microscope.remote.client.InstancePage;
import cafe.jeffrey.microscope.remote.client.InstanceRead;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many questions each read asks of the volume. On an Azure file share every one is an HTTPS
 * round-trip made while a page waits, so the counts are pinned: a change that asks again for what
 * a listing already answered fails here, not on a slow page.
 */
class VolumeCallBudgetTest {

    private static final Instant T0 = Instant.parse("2026-10-26T06:00:00Z");
    /** After the newest session started; sessions start two hours apart. */
    private static final Instant NOW = T0.plus(Duration.ofHours(24));
    private static final ProjectScope ORDERS = new ProjectScope("prod", "p-orders");
    private static final int SESSIONS = 12;
    private static final int CHUNKS_PER_SESSION = 20;
    /** Each session named as the provisioner names it, for its start: oldest first. */
    private static final List<String> SESSION_IDS = IntStream.range(0, SESSIONS)
            .mapToObj(session -> VolumeWriter.sessionIdAt(startOf(session)))
            .toList();
    private static final String LIVE_SESSION = SESSION_IDS.getLast();
    private static final String FINISHED_SESSION = SESSION_IDS.getFirst();

    @TempDir
    Path root;

    private CountingVolume volume;
    private RemoteClients clients;

    /**
     * Records every question asked of the volume it wraps, and lists each session directory with
     * the time it was created, as an SMB share lists it, or no time at all, as an NFS share does —
     * neither of which a session's order depends on, its name carrying its start.
     */
    private static final class CountingVolume implements WritableVolume {

        private final FilesystemVolume delegate;
        private final boolean listsTimes;
        // synchronized: a share is read through a fan-out of eight readers at once
        private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        private CountingVolume(FilesystemVolume delegate, boolean listsTimes) {
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
            if (listsTimes || !entry.isDirectory() || !SESSION_IDS.contains(entry.name())) {
                return entry;
            }
            return new VolumeEntry(entry.path(), entry.kind(), entry.size(), Optional.empty());
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

        @Override
        public void createDirectories(VolumePath directory) {
            calls.add("mkdir " + directory);
            delegate.createDirectories(directory);
        }

        @Override
        public void write(VolumePath file, byte[] content) {
            calls.add("write " + file);
            delegate.write(file, content);
        }

        @Override
        public void delete(VolumePath path) {
            calls.add("delete " + path);
            delegate.delete(path);
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
            Path session = VolumeWriter.session(instance, SESSION_IDS.get(s), start, null);
            for (int c = 0; c < CHUNKS_PER_SESSION; c++) {
                VolumeWriter.file(session, "profile-%02d%02d.jfr.lz4".formatted(s, c), "x");
            }
            VolumeWriter.file(session, "jvm.log", "x");
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
                volume, clock, "budget", true));
    }

    /** As Microscope reads a share: through the memo of what never changes, the reads several at a time. */
    private RemoteClients asMicroscopeReadsAShare() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        volume = new CountingVolume(new FilesystemVolume(root), true);
        RememberingVolume remembering = new RememberingVolume(volume, clock);
        return VolumeRemoteClients.of(new VolumeSource(
                remembering, clock, "budget", true,
                new BoundedReadFanOut(BoundedReadFanOut.DEFAULT_MAX_IN_FLIGHT)));
    }

    /** What the instance page asks for: its project found, as every project-scoped request does, then its page. */
    private void openTheInstancePage(RemoteClients over) {
        over.discovery().project(ORDERS.workspaceId(), ORDERS.projectId()).orElseThrow();
        over.instances().instance(ORDERS, "pod-1", InstancePage.newest(Optional.empty())).orElseThrow();
    }

    @Test
    void anInstanceWithItsSessionsCostsTwoQuestionsASession() {
        clients.instances().instance(ORDERS, "pod-1", InstancePage.whole());

        // workspace listing + project marker, instance marker, instance listing; then per session its
        // marker and its listing
        assertEquals(4 + 2 * SESSIONS, volume.countAndReset());
    }

    @Test
    void theProjectsInstancesReadOnlyTheNewestSession() {
        clients.instances().projectInstances(ORDERS);

        // workspace listing + project marker, project listing, instance marker, instance listing, whose
        // names say which session is the newest and how many there are; its marker and its listing
        assertEquals(5 + 2, volume.countAndReset());
    }

    @Test
    void everySessionOfTheProjectCostsTwoQuestionsASession() {
        clients.repository().recordingSessions(ORDERS, RecordingSessionFilter.ALL);

        // as one instance, plus the project listing
        assertEquals(5 + 2 * SESSIONS, volume.countAndReset());
    }

    @Test
    void anOlderSessionReadsOnlyTheNextOneAlong() {
        clients.repository().recordingSession(ORDERS, FINISHED_SESSION);

        // workspace listing + project marker, project listing, its marker, its instance's marker, the
        // instance listing, the marker of the next session its names point to, and its own listing
        assertEquals(8, volume.countAndReset());
    }

    @Test
    void theLiveSessionKnowsItIsTheNewestFromTheInstanceListing() {
        clients.repository().recordingSession(ORDERS, LIVE_SESSION);

        // as an older one, but the instance listing's names say no sibling started after it, so no
        // sibling's marker is read
        assertEquals(7, volume.countAndReset());
    }

    @Test
    void withoutListedTimesTheLiveSessionStillReadsNoSibling() {
        clientsOver(false).repository().recordingSession(ORDERS, LIVE_SESSION);

        // an NFS share lists no times; the names say the same as on any share
        assertEquals(7, volume.countAndReset());
    }

    @Test
    void theInstancePageReadsOnlyItsNewestSessions() {
        InstanceRead page = clients.instances().instance(ORDERS, "pod-1", InstancePage.newest(Optional.empty()))
                .orElseThrow();

        // workspace listing + project marker, instance marker, instance listing; then per page
        // session its marker and its listing
        assertEquals(4 + 2 * InstancePage.PAGE_SIZE, volume.countAndReset());
        assertEquals(LIVE_SESSION, page.sessions().getFirst().id());
        assertEquals(InstancePage.PAGE_SIZE, page.sessions().size());
        assertTrue(page.hasOlder());
        assertEquals(SESSIONS, page.instance().sessionCount());
        assertEquals(CHUNKS_PER_SESSION + 1, page.sessions().getFirst().files().size(),
                "each session carries its files, so opening one asks for nothing");
    }

    @Test
    void withoutListedTimesTheInstancePageStillReadsOnlyItsNewestSessions() {
        InstanceRead page = clientsOver(false).instances()
                .instance(ORDERS, "pod-1", InstancePage.newest(Optional.empty()))
                .orElseThrow();

        // as on a share that lists times: the names order the page, so no other session's marker is read
        assertEquals(4 + 2 * InstancePage.PAGE_SIZE, volume.countAndReset());
        assertEquals(LIVE_SESSION, page.sessions().getFirst().id());
        assertEquals(SESSIONS, page.instance().sessionCount());
    }

    @Test
    void theOlderPageStartsAfterTheCursor() {
        String lastOnFirstPage = SESSION_IDS.get(SESSIONS - InstancePage.PAGE_SIZE);

        InstanceRead older = clients.instances()
                .instance(ORDERS, "pod-1", InstancePage.newest(Optional.of(lastOnFirstPage)))
                .orElseThrow();

        assertEquals(List.of(SESSION_IDS.get(1), FINISHED_SESSION),
                older.sessions().stream().map(RecordingSessionResponse::id).toList());
        assertFalse(older.hasOlder());
        // as the first page for its two sessions, plus the newest session's marker and listing for the
        // instance's summary
        assertEquals(4 + 2 * 2 + 2, volume.countAndReset());
    }

    @Test
    void findingTheProjectReadsOnlyItsMarker() {
        clients.discovery().project(ORDERS.workspaceId(), ORDERS.projectId()).orElseThrow();

        // the workspace listing and the project marker; nothing below the project
        assertEquals(2, volume.countAndReset());
    }

    @Nested
    class AsMicroscopeReadsAShare {

        @Test
        void theFirstOpeningOfTheInstancePage() {
            openTheInstancePage(asMicroscopeReadsAShare());

            // the project found (2), then the page as above, whose own look for the project
            // takes the project marker just remembered
            assertEquals(2 + 3 + 2 * InstancePage.PAGE_SIZE, volume.countAndReset());
        }

        @Test
        void openingItAgainAsksOnlyWhatCanHaveChanged() {
            RemoteClients over = asMicroscopeReadsAShare();
            openTheInstancePage(over);
            volume.countAndReset();

            openTheInstancePage(over);

            // two workspace listings, the instance listing and each page session's listing; every
            // marker remembered
            assertEquals(2 + 1 + InstancePage.PAGE_SIZE, volume.countAndReset());
        }
    }
}