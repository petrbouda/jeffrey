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

import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.hub.core.project.repository.HubManagedFile;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one figure repository statistics carries: the bytes every session of the project occupies.
 *
 * <p>Summed over every file of every session, recordings and artifacts alike, so the number is the
 * one a reader would get from {@code du} on the project directory. A file whose size the listing
 * could not read counts as zero rather than failing the sum — the figure is a headline on a card,
 * and one unreadable entry must not blank it.
 */
class RepositoryManagerImplStatisticsTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");

    private final RepositoryStorage repositoryStorage = mock(RepositoryStorage.class);

    private static RepositoryFile file(String name, Long size) {
        return new RepositoryFile(name, name, T0, size, HubManagedFile.of(name).isPresent(), null);
    }

    private static RecordingSession session(String id, RecordingStatus status, List<RepositoryFile> files) {
        return new RecordingSession(id, id, "inst-1", T0, null, status, null, files, false);
    }

    private RepositoryManagerImpl managerOf(List<RecordingSession> sessions) {
        when(repositoryStorage.listSessions(true)).thenReturn(sessions);

        return new RepositoryManagerImpl(
                Clock.fixed(T0, ZoneOffset.UTC),
                mock(ProjectInfo.class),
                mock(ProjectRepositoryRepository.class),
                mock(ProjectInstanceRepository.class),
                repositoryStorage,
                TransactionOperations.withoutTransaction());
    }

    @Test
    void sumsEveryFileOfEverySession() {
        RepositoryManagerImpl manager = managerOf(List.of(
                session("s-1", RecordingStatus.FINISHED, List.of(
                        file("profile-1.jfr.lz4", 1_000L),
                        file("heap.hprof", 20_000L))),
                session("s-2", RecordingStatus.ACTIVE, List.of(
                        file("profile-2.jfr", 300L)))));

        assertEquals(new RepositoryStatistics(21_300L), manager.calculateRepositoryStatistics());
    }

    @Test
    void countsAFileWithNoReadableSizeAsZero() {
        RepositoryManagerImpl manager = managerOf(List.of(
                session("s-1", RecordingStatus.ACTIVE, List.of(
                        file("profile-1.jfr", 500L),
                        file("profile-2.jfr", null)))));

        assertEquals(new RepositoryStatistics(500L), manager.calculateRepositoryStatistics());
    }

    @Test
    void isZeroForAProjectWithNoSessions() {
        assertEquals(new RepositoryStatistics(0L), managerOf(List.of()).calculateRepositoryStatistics());
    }
}
