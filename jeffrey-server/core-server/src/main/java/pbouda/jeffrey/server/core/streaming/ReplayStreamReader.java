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

package pbouda.jeffrey.server.core.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Composite reader that iterates over all recording files in a {@link ReplayStreamSubscription}
 * and delegates each file to a {@link SingleRecordingFileReader}.
 * Runs asynchronously on a virtual thread from {@link Schedulers#streamingExecutor()}.
 *
 * <p>Creates a dedicated temp directory for decompressed files and removes
 * the entire directory on {@link #close()}.</p>
 */
public class ReplayStreamReader implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayStreamReader.class);

    private final ReplayStreamSubscription subscription;
    private final StreamingCallbacks callbacks;
    private final Path replayTempDir;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ReplayStreamReader(
            ReplayStreamSubscription subscription,
            StreamingCallbacks callbacks) {

        this.subscription = subscription;
        this.callbacks = callbacks;
        this.replayTempDir = subscription.tempDir()
                .resolve("replay-" + subscription.sessionId() + "-" + IDGenerator.generate());
    }

    /**
     * Starts reading all recording files asynchronously on a virtual thread.
     */
    public void start() {
        LOG.info("Starting replay stream: subscription={}", subscription);
        FileSystemUtils.createDirectories(replayTempDir);
        Schedulers.streamingExecutor().execute(this::readAllFiles);
    }

    private void readAllFiles() {
        SingleRecordingFileReader fileReader =
                new SingleRecordingFileReader(subscription, replayTempDir, callbacks.onNext(), closed::get);

        try {
            for (Path file : subscription.recordingFiles()) {
                if (closed.get()) {
                    break;
                }
                try {
                    fileReader.read(file);
                } catch (Exception e) {
                    LOG.warn("Skipping corrupted recording file: file={} subscription={}", file.getFileName(), subscription, e);
                }
            }

            if (!closed.get()) {
                callbacks.onComplete().run();
            }
        } catch (Exception e) {
            if (!closed.get()) {
                LOG.error("Error reading recording files: subscription={}", subscription, e);
                callbacks.onError().accept(e);
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.info("Closing replay stream reader: subscription={}", subscription);
            FileSystemUtils.removeDirectory(replayTempDir);
        }
    }
}
