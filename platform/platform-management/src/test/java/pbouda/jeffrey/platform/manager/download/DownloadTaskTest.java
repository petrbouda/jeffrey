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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class DownloadTaskTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private DownloadTask task;

    @BeforeEach
    void setUp() {
        task = new DownloadTask("ws-1", "proj-1", "session-1", List.of("f1", "f2"), FIXED_CLOCK);
    }

    @Nested
    class StatusTransitions {

        @Test
        void initialStatus_isPending() {
            assertEquals(DownloadTaskStatus.PENDING, task.getCurrentProgress().status());
        }

        @Test
        void onStart_transitionsToDownloading() {
            task.onStart(2, 1000L);

            assertEquals(DownloadTaskStatus.DOWNLOADING, task.getCurrentProgress().status());
        }

        @Test
        void onComplete_transitionsToCompleted() {
            task.onStart(2, 1000L);
            task.onComplete();

            assertEquals(DownloadTaskStatus.COMPLETED, task.getCurrentProgress().status());
        }

        @Test
        void onError_transitionsToFailed() {
            task.onStart(2, 1000L);
            task.onError("Something went wrong");

            assertEquals(DownloadTaskStatus.FAILED, task.getCurrentProgress().status());
        }

        @Test
        void cancel_transitionsToCancelled() {
            task.cancel();

            assertEquals(DownloadTaskStatus.CANCELLED, task.getCurrentProgress().status());
        }

        @Test
        void cancel_isIdempotent() {
            task.cancel();
            task.cancel();

            assertEquals(DownloadTaskStatus.CANCELLED, task.getCurrentProgress().status());
            assertTrue(task.isCancelled());
        }
    }

    @Nested
    class ProgressTracking {

        @Test
        void onFileStart_movesFileFromPendingToActive() {
            task.onStart(2, 1000L);
            task.onFilesDiscovered(List.of(
                    FileProgress.pending("file-a.jfr", 500L),
                    FileProgress.pending("file-b.jfr", 500L)));

            task.onFileStart("file-a.jfr", 500L);

            DownloadProgress progress = task.getCurrentProgress();
            assertEquals(1, progress.activeDownloads().size());
            assertEquals(1, progress.pendingDownloads().size());
            assertEquals("file-a.jfr", progress.activeDownloads().getFirst().fileName());
        }

        @Test
        void onFileProgress_updatesDownloadedBytes() {
            task.onStart(1, 1000L);
            task.onFileStart("file-a.jfr", 1000L);
            task.onFileProgress("file-a.jfr", 500L);

            DownloadProgress progress = task.getCurrentProgress();
            assertEquals(500L, progress.downloadedBytes());
        }

        @Test
        void onFileComplete_movesFileFromActiveToCompleted_andIncrementsCounts() {
            task.onStart(2, 1000L);
            task.onFileStart("file-a.jfr", 500L);
            task.onFileComplete("file-a.jfr");

            DownloadProgress progress = task.getCurrentProgress();
            assertEquals(0, progress.activeDownloads().size());
            assertEquals(1, progress.completedDownloads().size());
            assertEquals(1, progress.completedFiles());
            assertEquals(500L, progress.downloadedBytes());
        }

        @Test
        void onFileError_removesFromActive_andTracksPartialBytes() {
            task.onStart(2, 1000L);
            task.onFileStart("file-a.jfr", 500L);
            task.onFileProgress("file-a.jfr", 200L);
            task.onFileError("file-a.jfr", "connection reset");

            DownloadProgress progress = task.getCurrentProgress();
            assertEquals(0, progress.activeDownloads().size());
            assertEquals(200L, progress.downloadedBytes());
        }

        @Test
        void percentComplete_calculatedCorrectly() {
            task.onStart(1, 1000L);
            task.onFileStart("file-a.jfr", 1000L);
            task.onFileProgress("file-a.jfr", 500L);

            assertEquals(50, task.getCurrentProgress().percentComplete());
        }

        @Test
        void percentComplete_cappedAt100() {
            task.onStart(1, 100L);
            task.onFileStart("file-a.jfr", 100L);
            task.onFileComplete("file-a.jfr");

            assertTrue(task.getCurrentProgress().percentComplete() <= 100);
        }
    }

    @Nested
    class ListenerNotification {

        @Test
        void addListener_receivesImmediateProgressSnapshot() {
            AtomicReference<DownloadProgress> received = new AtomicReference<>();
            task.addProgressListener(received::set);

            assertNotNull(received.get());
            assertEquals(DownloadTaskStatus.PENDING, received.get().status());
        }

        @Test
        void allCallbacks_notifyListeners() {
            AtomicReference<DownloadProgress> latest = new AtomicReference<>();
            task.addProgressListener(latest::set);

            task.onStart(1, 1000L);
            assertEquals(DownloadTaskStatus.DOWNLOADING, latest.get().status());

            task.onFileStart("file-a.jfr", 1000L);
            assertEquals(1, latest.get().activeDownloads().size());

            task.onComplete();
            assertEquals(DownloadTaskStatus.COMPLETED, latest.get().status());
        }

        @Test
        void listenerException_doesNotPropagateToTask() {
            // First trigger onStart to move past PENDING, then add the broken listener
            task.onStart(1, 1000L);

            Consumer<DownloadProgress> brokenListener = p -> {
                if (p.status() != DownloadTaskStatus.DOWNLOADING) {
                    throw new RuntimeException("listener error");
                }
            };
            // addProgressListener sends immediate snapshot (DOWNLOADING) — no exception
            task.addProgressListener(brokenListener);

            // onComplete triggers notifyListeners which catches exceptions
            assertDoesNotThrow(() -> task.onComplete());
            assertEquals(DownloadTaskStatus.COMPLETED, task.getCurrentProgress().status());
        }
    }

    @Nested
    class Cancellation {

        @Test
        void cancel_cancelsFuture_whenFutureIsSet() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            task.setDownloadFuture(future);

            task.cancel();

            assertTrue(future.isCancelled());
            assertTrue(task.isCancelled());
        }

        @Test
        void isCancelled_returnsTrueAfterCancel() {
            assertFalse(task.isCancelled());
            task.cancel();
            assertTrue(task.isCancelled());
        }
    }
}
