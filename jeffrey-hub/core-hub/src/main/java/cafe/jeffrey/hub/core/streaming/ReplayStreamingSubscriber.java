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

package cafe.jeffrey.hub.core.streaming;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.ReplayStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Composite reader that iterates over all recording files in a {@link ReplayStreamSubscription}
 * and delegates each file to a {@link SingleReplyStreamingSubscriber}.
 * Runs asynchronously on a virtual thread from {@link Schedulers#streamingExecutor()}.
 *
 * <p>Creates a dedicated temp directory for decompressed files and removes
 * the entire directory after its reader exits, including after {@link #close()}.</p>
 */
public class ReplayStreamingSubscriber implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayStreamingSubscriber.class);

    private final ReplayStreamSubscription subscription;
    private final StreamingCallbacks callbacks;
    private final Path replayTempDir;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean cleaned = new AtomicBoolean(false);
    private final AtomicLong sourceErrors = new AtomicLong();
    private final SingleReplyStreamingSubscriber fileReader;
    private final Executor scheduler;
    private boolean started;

    public ReplayStreamingSubscriber(ReplayStreamSubscription subscription, StreamingCallbacks callbacks) {
        this(subscription, callbacks, Schedulers.streamingExecutor());
    }

    ReplayStreamingSubscriber(ReplayStreamSubscription subscription, StreamingCallbacks callbacks, Executor scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.subscription = subscription;
        this.callbacks = callbacks;
        this.replayTempDir = subscription.tempDir()
                .resolve("replay-" + IDGenerator.generate());
        this.fileReader = new SingleReplyStreamingSubscriber(subscription, replayTempDir, callbacks.onNext(),
                closed::get, sourceErrors::incrementAndGet);
    }

    /**
     * Starts reading all recording files asynchronously on a virtual thread.
     */
    public synchronized void start() {
        if (closed.get()) {
            return;
        }
        started = true;
        LOG.info("Starting replay stream: subscription={}", subscription);
        try {
            FileSystemUtils.createDirectories(replayTempDir);
            scheduler.execute(this::readAllFiles);
        } catch (RuntimeException e) {
            // No reader will reach its finally block. Release scratch and the manager's
            // registration here, and let the caller report the startup failure.
            closed.set(true);
            try {
                cleanup();
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private void readAllFiles() {
        try {
            if (subscription.reportCoverage() && !closed.get()) {
                callbacks.onNext().accept(EventBatch.newBuilder().setReplayStatus(ReplayStatus.newBuilder()
                        .setWorkspaceId(subscription.workspaceId()).setProjectId(subscription.projectId())).build());
            }
            for (Path file : subscription.recordingFiles()) {
                if (closed.get()) {
                    break;
                }
                try {
                    fileReader.read(file);
                } catch (Exception e) {
                    // A corrupted file is recoverable: skip it and continue with the remaining
                    // files. The terminal onError is reserved for fatal errors — calling it here
                    // would close the stream and make every subsequent onNext/onComplete illegal.
                    sourceErrors.incrementAndGet();
                    LOG.warn("Skipping corrupted recording file: file={} subscription={}",
                            file.getFileName(), subscription, e);
                }
            }

            if (!closed.get()) {
                if (subscription.reportCoverage()) {
                    callbacks.onNext().accept(EventBatch.newBuilder().setReplayStatus(ReplayStatus.newBuilder()
                            .setTerminal(true).setSourceErrors(sourceErrors.get())).build());
                }
                callbacks.onComplete().run();
            }
        } catch (Exception e) {
            if (!closed.get()) {
                LOG.error("Error reading recording files: subscription={}", subscription, e);
                callbacks.onError().accept(e);
            }
        } finally {
            closed.set(true);
            fileReader.close();
            cleanup();
        }
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            fileReader.close();
            if (!started) {
                cleanup();
            }
        }
    }

    private void cleanup() {
        if (cleaned.compareAndSet(false, true)) {
            try {
                FileSystemUtils.removeDirectory(replayTempDir);
            } finally {
                callbacks.onClose().run();
            }
        }
    }
}
