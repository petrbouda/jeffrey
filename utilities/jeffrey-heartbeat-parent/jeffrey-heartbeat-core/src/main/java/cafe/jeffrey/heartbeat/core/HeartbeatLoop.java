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
package cafe.jeffrey.heartbeat.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Rewrites the heartbeat file at a fixed interval and writes the clean-exit marker when closed.
 * Both files are written through a scratch file and a rename, so the hub never reads a torn value.
 *
 * <p>Nothing here fails the caller: a directory that cannot be created means no loop, and a beat
 * that cannot be written is logged at debug and retried at the next interval.
 */
public final class HeartbeatLoop implements AutoCloseable {

    private static final String TEMPORARY_SUFFIX = ".tmp";
    private static final String THREAD_NAME = "jeffrey-heartbeat";

    /**
     * How long {@link #close()} waits for a beat already writing. Bounded because it runs on the
     * shutdown path: a slow volume may delay an exit by a moment, never hold it open.
     */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(2);

    private final Path heartbeatFile;
    private final Path heartbeatTemporaryFile;
    private final Path finishedFile;
    private final Path finishedTemporaryFile;
    private final Clock clock;
    private final HeartbeatLog log;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    private HeartbeatLoop(Path directory, Clock clock, HeartbeatLog log, ScheduledExecutorService scheduler) {
        this.heartbeatFile = directory.resolve(HeartbeatContract.HEARTBEAT_FILE);
        this.heartbeatTemporaryFile = directory.resolve(HeartbeatContract.HEARTBEAT_FILE + TEMPORARY_SUFFIX);
        this.finishedFile = directory.resolve(HeartbeatContract.FINISHED_FILE);
        this.finishedTemporaryFile = directory.resolve(HeartbeatContract.FINISHED_FILE + TEMPORARY_SUFFIX);
        this.clock = clock;
        this.log = log;
        this.scheduler = scheduler;
    }

    /**
     * Starts beating into {@code directory}, creating it when missing.
     *
     * @return the running loop, or empty when the directory cannot be created
     */
    public static Optional<HeartbeatLoop> start(Path directory, Duration interval, Clock clock, HeartbeatLog log) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.warn("Jeffrey heartbeat directory cannot be created, liveness will not be reported: directory="
                    + directory, e);
            return Optional.empty();
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            // Daemon: reporting liveness must never be the reason a JVM stays up
            thread.setDaemon(true);
            return thread;
        });

        HeartbeatLoop loop = new HeartbeatLoop(directory, clock, log, scheduler);
        // Zero initial delay: the first beat is what tells the hub this session reports liveness at
        // all, and from then on it is held to the heartbeat deadline
        scheduler.scheduleAtFixedRate(loop::beat, 0, interval.toMillis(), TimeUnit.MILLISECONDS);

        log.info("Jeffrey heartbeat started: directory=" + directory + " interval=" + interval);
        return Optional.of(loop);
    }

    /** Whether this loop is still beating. */
    public boolean running() {
        return !closed.get();
    }

    /**
     * Stops beating and writes the clean-exit marker, which lets the hub finish the session at once
     * instead of waiting for the heartbeat to go stale. A beat already in flight is waited for
     * rather than interrupted: tearing it down mid-write is how a rename fails or a beat lands
     * after the marker it should precede. Closing twice is a no-op.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        awaitLastBeat();
        try {
            write(finishedTemporaryFile, finishedFile, clock.millis());
        } catch (IOException | RuntimeException e) {
            // The session still finishes, from the last heartbeat, a threshold later
            log.warn("Jeffrey clean-exit marker could not be written", e);
        }
        try {
            Files.deleteIfExists(heartbeatTemporaryFile);
        } catch (IOException e) {
            // best-effort cleanup: a scratch file left behind is untidy, never harmful
        }
    }

    /**
     * Refuses new beats and gives the running one {@link #SHUTDOWN_GRACE} to finish, forcing it
     * only if it overruns — at which point a stuck volume is the problem and the marker matters more.
     */
    private void awaitLastBeat() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private void beat() {
        try {
            write(heartbeatTemporaryFile, heartbeatFile, clock.millis());
        } catch (IOException | RuntimeException e) {
            // Debug: a briefly unwritable volume would otherwise fill the log at the beat interval
            log.debug("Jeffrey heartbeat could not be written", e);
        }
    }

    private static void write(Path temporaryFile, Path targetFile, long epochMillis) throws IOException {
        Files.writeString(temporaryFile, Long.toString(epochMillis));
        try {
            Files.move(temporaryFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, targetFile, REPLACE_EXISTING);
        }
    }
}
