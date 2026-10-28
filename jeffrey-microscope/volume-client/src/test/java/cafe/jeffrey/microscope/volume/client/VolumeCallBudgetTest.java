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
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionHeaderResponse;
import cafe.jeffrey.shared.common.exception.JeffreyException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many questions each read asks of the volume. On an Azure file share every one is an HTTPS
 * round-trip made while a page waits, so the counts are pinned: a change that asks again for what
 * a listing already answered fails here, not on a slow page. Nothing below a workspace is a file to
 * read: a level is one listing of its parent, whatever it holds.
 */
class VolumeCallBudgetTest {

    private static final Instant T0 = Instant.parse("2026-10-26T06:00:00Z");
    /** After the newest session started; sessions start two hours apart. */
    private static final Instant NOW = T0.plus(Duration.ofHours(24));
    private static final ProjectScope ORDERS = new ProjectScope("prod", "orders");
    private static final int SESSIONS = 12;
    private static final int CHUNKS_PER_SESSION = 20;
    /** Projects beside {@code orders}, and instances beside {@code pod-1}: a list costs the same whatever it holds. */
    private static final int OTHER_PROJECTS = 9;
    private static final int OTHER_INSTANCES = 4;
    /** Each session named as the provisioner names it, for its start: oldest first. */
    private static final List<String> SESSION_IDS = IntStream.range(0, SESSIONS)
            .mapToObj(session -> VolumeWriter.sessionIdAt(startOf(session)))
            .toList();
    private static final String NEWEST_SESSION = SESSION_IDS.getLast();
    private static final String OLDER_SESSION = SESSION_IDS.getFirst();

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
     * A workspace of ten projects. {@code orders} has five instances; {@code pod-1} holds twelve
     * sessions two hours apart of twenty chunks and a log each, the newest last; the others one
     * session each.
     */
    @BeforeEach
    void setUp() throws IOException {
        Path workspace = Files.createDirectories(root.resolve("prod"));
        Path project = VolumeWriter.project(workspace, "orders");
        for (int p = 0; p < OTHER_PROJECTS; p++) {
            VolumeWriter.session(VolumeWriter.instance(VolumeWriter.project(workspace, "app-" + p), "pod-1"), T0);
        }
        for (int i = 0; i < OTHER_INSTANCES; i++) {
            VolumeWriter.session(VolumeWriter.instance(project, "pod-other-" + i), T0.plusSeconds(i + 1));
        }
        Path instance = VolumeWriter.instance(project, "pod-1");
        for (int s = 0; s < SESSIONS; s++) {
            Path session = VolumeWriter.session(instance, startOf(s));
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
        return VolumeRemoteClients.of(new VolumeSource(volume, clock, "budget", true));
    }

    /** As Microscope reads a share: the independent reads of a call several at a time. */
    private RemoteClients asMicroscopeReadsAShare() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        volume = new CountingVolume(new FilesystemVolume(root), true);
        return VolumeRemoteClients.of(new VolumeSource(volume, clock, "budget", true,
                new BoundedReadFanOut(BoundedReadFanOut.DEFAULT_MAX_IN_FLIGHT)));
    }

    /** What the instance page asks for: its project found, as every project-scoped request does, then its page. */
    private static InstanceRead openTheInstancePage(RemoteClients over) {
        over.discovery().project(ORDERS.workspaceId(), ORDERS.projectId()).orElseThrow();
        return over.instances().instance(ORDERS, "pod-1", InstancePage.newest(Optional.empty())).orElseThrow();
    }

    @Nested
    class EachListIsOneListing {

        @Test
        void theWorkspaces() {
            clients.discovery().allWorkspaces();

            assertEquals(1, volume.countAndReset());
        }

        @Test
        void aWorkspacesProjectsWhateverItHolds() {
            assertEquals(1 + OTHER_PROJECTS, clients.discovery().allProjects("prod").size());

            assertEquals(1, volume.countAndReset());
        }

        @Test
        void aProjectsInstancesWhateverTheyHold() {
            assertEquals(1 + OTHER_INSTANCES, clients.instances().projectInstances(ORDERS).size());

            assertEquals(1, volume.countAndReset());
        }

        /** A look at the instance, so an unknown one is not found, and one listing of it; no session read. */
        @Test
        void anInstancesSessionsWhateverTheirNumber() {
            InstanceRead page = clients.instances().instance(ORDERS, "pod-1", InstancePage.whole()).orElseThrow();

            assertEquals(2, volume.countAndReset());
            assertEquals(SESSIONS, page.sessions().size());
            assertEquals(NEWEST_SESSION, page.sessions().getFirst().id());
            assertTrue(page.sessions().getFirst().newest());
        }

        @Test
        void withoutListedTimesStillOneListing() {
            clientsOver(false).instances().instance(ORDERS, "pod-1", InstancePage.whole()).orElseThrow();

            assertEquals(2, volume.countAndReset());
        }
    }

    @Nested
    class LookingUp {

        /** One look at the project's directory, named by its id; nothing below it. */
        @Test
        void aProjectIsOneLook() {
            clients.discovery().project(ORDERS.workspaceId(), ORDERS.projectId()).orElseThrow();

            assertEquals(1, volume.countAndReset());
        }

        /** A look at the directory and its marker, for the name it may go by. */
        @Test
        void aWorkspaceIsOneLookAndItsMarker() {
            clients.discovery().workspaceOrThrow("prod").orElseThrow();

            assertEquals(3, volume.countAndReset(), "the look, the marker, and the directory's time when no marker is there");
        }

        @Test
        void theInstancePageAfterItsProject() {
            InstanceRead page = openTheInstancePage(clients);

            assertEquals(1 + 2, volume.countAndReset());
            assertEquals(InstancePage.PAGE_SIZE, page.sessions().size());
            assertTrue(page.hasOlder());
        }

        @Test
        void theOlderPageStartsAfterTheCursor() {
            String lastOnFirstPage = SESSION_IDS.get(SESSIONS - InstancePage.PAGE_SIZE);

            InstanceRead older = clients.instances()
                    .instance(ORDERS, "pod-1", InstancePage.newest(Optional.of(lastOnFirstPage)))
                    .orElseThrow();

            assertEquals(List.of(SESSION_IDS.get(1), OLDER_SESSION),
                    older.sessions().stream().map(RecordingSessionHeaderResponse::id).toList());
            assertFalse(older.hasOlder());
            assertEquals(2, volume.countAndReset());
        }
    }

    @Nested
    class OpeningASession {

        /**
         * The project's listing, a look into each instance by name until one holds the session —
         * {@code pod-1}, the first — its own listing, and its instance's listing to say whether it
         * is the newest.
         */
        @Test
        void theNewestReadsItsListingAndItsInstances() {
            clients.repository().recordingSession(ORDERS, NEWEST_SESSION);

            assertEquals(1 + 1 + 1 + 1, volume.countAndReset());
        }

        @Test
        void anOlderOneTheSame() {
            clients.repository().recordingSession(ORDERS, OLDER_SESSION);

            assertEquals(1 + 1 + 1 + 1, volume.countAndReset());
        }

        /** Held by the last instance by name: a look into each before it. */
        @Test
        void inTheLastInstanceAfterALookIntoEach() {
            String last = VolumeWriter.sessionIdAt(T0.plusSeconds(OTHER_INSTANCES));

            clients.repository().recordingSession(ORDERS, last);

            assertEquals(1 + (1 + OTHER_INSTANCES) + 1 + 1, volume.countAndReset());
        }

        @Test
        void anIdThatIsNoSessionsNameAsksNothing() {
            assertThrows(JeffreyException.class, () -> clients.repository().recordingSession(ORDERS, "not-a-session"));

            assertEquals(0, volume.countAndReset());
        }
    }

    /** Every session of the project, as MCP's session search reads them: each instance's listing, each session's. */
    @Test
    void everySessionOfTheProjectCostsOneListingASession() {
        clients.repository().recordingSessions(ORDERS, RecordingSessionFilter.ALL);

        int instances = 1 + OTHER_INSTANCES;
        assertEquals(1 + instances + SESSIONS + OTHER_INSTANCES, volume.countAndReset());
    }

    @Nested
    class AsMicroscopeReadsAShare {

        /** Nothing is remembered between calls: opening the page again asks the same again, and no more. */
        @Test
        void openingTheInstancePageAgainAsksTheSame() {
            RemoteClients over = asMicroscopeReadsAShare();
            openTheInstancePage(over);
            int first = volume.countAndReset();

            openTheInstancePage(over);

            assertEquals(1 + 2, first);
            assertEquals(1 + 2, volume.countAndReset());
        }
    }
}
