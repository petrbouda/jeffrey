/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.project.repository.InstanceEnvironmentParser;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hub's one download path: every finished, non-transient file is served as it lies on disk,
 * and what is refused is refused with the sentence the client relays.
 */
class RepositoryManagerImplStreamFileTest {

    private static final String SESSION_ID = "session";

    @TempDir
    Path sessionDir;

    private final RepositoryStorage storage = mock(RepositoryStorage.class);
    private RepositoryManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new RepositoryManagerImpl(
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                mock(ProjectInfo.class),
                mock(ProjectRepositoryRepository.class),
                mock(ProjectInstanceRepository.class),
                storage,
                mock(InstanceEnvironmentParser.class),
                mock(TransactionOperations.class));
    }

    private RepositoryFile onDisk(String id, String name, RecordingStatus status) throws IOException {
        Path path = Files.writeString(sessionDir.resolve(name), "bytes");
        return new RepositoryFile(id, name, Instant.EPOCH, 5L, SupportedFile.of(name), status, path);
    }

    private void sessionHolds(RepositoryFile... files) {
        RecordingSession session = new RecordingSession(
                SESSION_ID, SESSION_ID, null, Instant.EPOCH, null, RecordingStatus.FINISHED, sessionDir, List.of(files), false);
        when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(session));
    }

    @Test
    void servesAChunkALogAndAnUnclassifiedFileAsTheyLieOnDisk() throws IOException {
        RepositoryFile chunk = onDisk("f-chunk", "profile-1.jfr.lz4", RecordingStatus.FINISHED);
        RepositoryFile log = onDisk("f-log", "gc.jvm-log", RecordingStatus.FINISHED);
        RepositoryFile unknown = onDisk("f-odd", "notes.txt", RecordingStatus.FINISHED);
        sessionHolds(chunk, log, unknown);

        for (RepositoryFile file : List.of(chunk, log, unknown)) {
            StreamedFile streamed = manager.streamFile(SESSION_ID, file.id());
            assertEquals(file.filePath(), streamed.path(), file.name());
            assertEquals(file.name(), streamed.fileName());
        }
    }

    @Test
    void refusesATransientFile() throws IOException {
        sessionHolds(onDisk("f-tmp", "profile-1.jfr.1~", RecordingStatus.FINISHED));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> manager.streamFile(SESSION_ID, "f-tmp"));

        assertTrue(refused.getMessage().contains("transient"), refused.getMessage());
    }

    @Test
    void refusesAFileStillBeingWritten() throws IOException {
        sessionHolds(onDisk("f-live", "profile-2.jfr", RecordingStatus.ACTIVE));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> manager.streamFile(SESSION_ID, "f-live"));

        assertTrue(refused.getMessage().contains("still being written"), refused.getMessage());
    }

    @Test
    void refusesAFileTheListingHasButTheDiskNoLongerHolds() throws IOException {
        RepositoryFile gone = onDisk("f-gone", "gc.jvm-log", RecordingStatus.FINISHED);
        sessionHolds(gone);
        Files.delete(gone.filePath());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> manager.streamFile(SESSION_ID, "f-gone"));

        assertTrue(refused.getMessage().contains("no longer on disk"), refused.getMessage());
    }

    @Test
    void refusesAnUnknownFileId() throws IOException {
        sessionHolds(onDisk("f-log", "gc.jvm-log", RecordingStatus.FINISHED));

        assertThrows(IllegalArgumentException.class, () -> manager.streamFile(SESSION_ID, "f-nope"));
    }
}
