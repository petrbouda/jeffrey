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

package cafe.jeffrey.agent;

import java.io.IOException;
import java.lang.System.Logger.Level;
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
 * Rewrites the heartbeat file at a fixed interval and writes the clean-exit marker on close.
 *
 * <p>The file names and their content — epoch millis, written through a scratch file and a
 * rename — are the hub's contract, the same one {@code JeffreyHeartbeat} in {@code jeffrey-heartbeat}
 * honours; the names are copied from {@code HeartbeatConstants}.
 */
public final class HeartbeatProducer implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(HeartbeatProducer.class.getName());

    static final String HEARTBEAT_FILE = "heartbeat";
    static final String FINISHED_FILE = "finished";
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
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean closed = new AtomicBoolean();

    private HeartbeatProducer(Path directory, Clock clock, ScheduledExecutorService scheduler) {
        this.heartbeatFile = directory.resolve(HEARTBEAT_FILE);
        this.heartbeatTemporaryFile = directory.resolve(HEARTBEAT_FILE + TEMPORARY_SUFFIX);
        this.finishedFile = directory.resolve(FINISHED_FILE);
        this.finishedTemporaryFile = directory.resolve(FINISHED_FILE + TEMPORARY_SUFFIX);
        this.clock = clock;
        this.scheduler = scheduler;
    }

    /**
     * Starts beating into {@code settings.directory()}, creating it when missing.
     *
     * @return the running producer, or empty when the directory cannot be created
     */
    static Optional<HeartbeatProducer> start(AgentSettings settings, Clock clock) {
        Path directory = settings.directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Jeffrey agent heartbeat directory cannot be created, liveness will "
                    + "not be reported: directory=" + directory, e);
            return Optional.empty();
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            // Daemon: reporting liveness must never be the reason a JVM stays up
            thread.setDaemon(true);
            return thread;
        });

        HeartbeatProducer producer = new HeartbeatProducer(directory, clock, scheduler);
        // Zero initial delay: the first beat is what tells the hub this session reports liveness
        scheduler.scheduleAtFixedRate(producer::beat, 0, settings.interval().toMillis(), TimeUnit.MILLISECONDS);

        LOG.log(Level.INFO, "Jeffrey agent heartbeat started: directory=" + directory
                + " interval=" + settings.interval());
        return Optional.of(producer);
    }

    /**
     * Stops beating and writes the clean-exit marker, which lets the hub finish the session at once
     * instead of waiting for the heartbeat to go stale. A beat already in flight is waited for
     * rather than interrupted: both files share the scratch-and-rename scheme, and tearing a beat
     * down mid-write is how a beat lands after the marker it should precede. Closing twice is a no-op.
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
            LOG.log(Level.WARNING, "Jeffrey agent clean-exit marker could not be written", e);
        }
        try {
            Files.deleteIfExists(heartbeatTemporaryFile);
        } catch (IOException e) {
            // best-effort cleanup: a scratch file left behind is untidy, never harmful
        }
    }

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
            LOG.log(Level.DEBUG, "Jeffrey agent heartbeat could not be written", e);
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
