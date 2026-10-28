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

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.volume.TestVolume;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a session takes its directory, and its instance's directory once no session is left —
 * an instance with nothing in it has nothing to show, and nothing on the volume would say why.
 */
class HubRepositoryManagerDeleteSessionTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final ProjectInfo PROJECT = new ProjectInfo("project-1", "project", "ws", Optional.of(T0));

    @TempDir
    Path workspacesDir;

    private Path instance;
    private HubRepositoryManager manager;

    @BeforeEach
    void setUp() {
        Path project = TestVolume.project(workspacesDir.resolve("ws"), PROJECT.name());
        instance = TestVolume.instance(project, "pod-a");
        manager = new HubRepositoryManager(PROJECT,
                TestVolume.storage(PROJECT, workspacesDir, project));
    }

    private static final String FIRST = TestVolume.sessionId(T0);

    private Path session(Instant start) {
        return TestVolume.session(instance, start);
    }

    /**
     * Pins and liveness files from before they were dropped are hidden entries like any other: they
     * protect nothing, and the session goes with them.
     */
    @Test
    void aLeftoverPinOrLivenessFileDoesNotKeepTheSession() throws IOException {
        Path older = session(T0);
        session(T0.plusSeconds(600));
        Files.createFile(older.resolve(".retained"));
        Files.createDirectories(older.resolve(".heartbeat"));
        Files.writeString(older.resolve(".heartbeat").resolve("finished"), "0");

        assertEquals(SessionDeletion.SESSION_DELETED, manager.deleteRecordingSession(FIRST));

        assertFalse(Files.exists(older));
    }

    @Test
    void deletesTheSessionAndKeepsAnInstanceThatStillHasOne() {
        Path older = session(T0);
        Path newer = session(T0.plusSeconds(600));

        assertEquals(SessionDeletion.SESSION_DELETED, manager.deleteRecordingSession(FIRST));

        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
        assertTrue(Files.exists(instance));
    }

    @Test
    void deletesTheInstanceWithItsLastSession() {
        session(T0);

        assertEquals(SessionDeletion.SESSION_AND_INSTANCE_DELETED, manager.deleteRecordingSession(FIRST));

        assertFalse(Files.exists(instance));
    }

    /** Any directory left in the instance keeps it: the instance goes only when it is empty. */
    @Test
    void keepsTheInstanceWhileAnyDirectoryIsInIt() throws IOException {
        session(T0);
        Path starting = Files.createDirectories(instance.resolve("about-to-start"));

        assertEquals(SessionDeletion.SESSION_DELETED, manager.deleteRecordingSession(FIRST));

        assertTrue(Files.exists(starting));
    }

    @Test
    void aDirectoryNotNamedForItsStartIsNoSession() throws IOException {
        Path other = Files.createDirectories(instance.resolve("not-a-session"));

        assertEquals(SessionDeletion.NOT_FOUND, manager.deleteRecordingSession("not-a-session"));
        assertTrue(Files.exists(other));
    }

    @Test
    void aSessionThatIsNotThereFreesNothing() {
        session(T0);

        assertEquals(SessionDeletion.NOT_FOUND, manager.deleteRecordingSession("ghost"));
        assertTrue(Files.exists(instance));
    }
}
