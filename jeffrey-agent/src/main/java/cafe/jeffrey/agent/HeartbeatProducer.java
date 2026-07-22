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

package cafe.jeffrey.agent;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.WARNING;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class HeartbeatProducer implements Closeable {

    private static final System.Logger LOG = System.getLogger(HeartbeatProducer.class.getName());

    // Duplicated from HeartbeatConstants — agent must stay zero-dependency for minimal JAR size
    private static final String HEARTBEAT_FILE_NAME = "heartbeat";
    private static final String HEARTBEAT_TMP_FILE_NAME = "heartbeat.tmp";
    private static final String FINISHED_FILE_NAME = "finished";
    private static final String FINISHED_TMP_FILE_NAME = "finished.tmp";

    private final Path heartbeatFile;
    private final Path heartbeatTmpFile;
    private final Path finishedFile;
    private final Path finishedTmpFile;
    private final Duration interval;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jeffrey-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public HeartbeatProducer(Path heartbeatDir, Duration interval) {
        this.heartbeatFile = heartbeatDir.resolve(HEARTBEAT_FILE_NAME);
        this.heartbeatTmpFile = heartbeatDir.resolve(HEARTBEAT_TMP_FILE_NAME);
        this.finishedFile = heartbeatDir.resolve(FINISHED_FILE_NAME);
        this.finishedTmpFile = heartbeatDir.resolve(FINISHED_TMP_FILE_NAME);
        this.interval = interval;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::beat, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void beat() {
        try {
            writeEpochFile(heartbeatTmpFile, heartbeatFile);
        } catch (Exception e) {
            LOG.log(WARNING, "Failed to write heartbeat file", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            // Clean-exit marker: its presence lets the hub finish the session
            // deterministically instead of waiting for the heartbeat to go stale.
            writeEpochFile(finishedTmpFile, finishedFile);
        } catch (Exception e) {
            LOG.log(WARNING, "Failed to write finished marker file", e);
        }
        try {
            Files.deleteIfExists(heartbeatTmpFile);
        } catch (IOException e) {
            // best-effort cleanup
        }
    }

    private static void writeEpochFile(Path tmpFile, Path targetFile) throws IOException {
        long now = System.currentTimeMillis();
        Files.writeString(tmpFile, Long.toString(now));
        try {
            Files.move(tmpFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmpFile, targetFile, REPLACE_EXISTING);
        }
    }
}
