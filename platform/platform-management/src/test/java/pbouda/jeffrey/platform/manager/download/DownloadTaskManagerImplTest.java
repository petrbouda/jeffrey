/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.download;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DownloadTaskManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private RecordingsDownloadManager recordingsDownloadManager;

    private DownloadTaskManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new DownloadTaskManagerImpl("ws-1", "proj-1", recordingsDownloadManager, FIXED_CLOCK);
    }

    @Nested
    class CreateTask {

        @Test
        void createsTask_andStoresInRegistry() {
            DownloadTask task = manager.createTask("session-1", List.of("f1", "f2"), true);

            assertNotNull(task);
            assertNotNull(task.getTaskId());
            assertEquals("session-1", task.getSessionId());
            assertEquals(List.of("f1", "f2"), task.getFileIds());
        }

        @Test
        void createdTask_isRetrievableByTaskId() {
            DownloadTask task = manager.createTask("session-1", List.of("f1"), true);

            Optional<DownloadTask> found = manager.getTask(task.getTaskId());

            assertTrue(found.isPresent());
            assertSame(task, found.get());
        }
    }

    @Nested
    class StartDownload {

        @Test
        void returnsFailedFuture_whenTaskNotFound() {
            CompletableFuture<Void> future = manager.startDownload("non-existent");

            assertTrue(future.isCompletedExceptionally());
            assertThrows(ExecutionException.class, future::get);
        }
    }

    @Nested
    class CancelDownload {

        @Test
        void returnsFalse_whenTaskNotFound() {
            assertFalse(manager.cancelDownload("non-existent"));
        }

        @Test
        void returnsFalse_whenTaskAlreadyTerminal() {
            DownloadTask task = manager.createTask("session-1", List.of("f1"), true);
            task.onStart(1, 100L);
            task.onComplete();

            assertFalse(manager.cancelDownload(task.getTaskId()));
        }

        @Test
        void returnsTrue_andCancelsTask_whenTaskIsActive() {
            DownloadTask task = manager.createTask("session-1", List.of("f1"), true);
            task.onStart(1, 100L);

            assertTrue(manager.cancelDownload(task.getTaskId()));
            assertTrue(task.isCancelled());
        }
    }

    @Nested
    class GetProgress {

        @Test
        void returnsProgress_whenTaskExists() {
            DownloadTask task = manager.createTask("session-1", List.of("f1"), true);

            Optional<DownloadProgress> progress = manager.getProgress(task.getTaskId());

            assertTrue(progress.isPresent());
            assertEquals(DownloadTaskStatus.PENDING, progress.get().status());
        }

        @Test
        void returnsEmpty_whenTaskNotFound() {
            Optional<DownloadProgress> progress = manager.getProgress("non-existent");

            assertTrue(progress.isEmpty());
        }
    }
}
