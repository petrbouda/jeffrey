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

package pbouda.jeffrey.platform.streaming;

import jdk.jfr.consumer.EventStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Schedulers;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes JFR events from a streaming repository for a specific session.
 * Uses {@link EventStream#openRepository(Path)} to open a live JFR repository
 * and delegates event processing to pluggable {@link JfrStreamingHandler} instances.
 */
public class JfrStreamingConsumer implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(JfrStreamingConsumer.class);

    private static final String STREAMING_REPO_DIR = "streaming-repo";

    private final String sessionId;
    private final Path streamingRepoPath;
    private final List<JfrStreamingHandler> handlers;
    private final Runnable onNaturalClose;
    private final Instant startTime;
    private final AtomicBoolean closedExplicitly = new AtomicBoolean(false);

    private EventStream eventStream;

    public JfrStreamingConsumer(
            String sessionId,
            Path sessionPath,
            List<JfrStreamingHandler> handlers,
            Runnable onNaturalClose,
            Instant startTime) {

        this.sessionId = sessionId;
        this.streamingRepoPath = sessionPath.resolve(STREAMING_REPO_DIR);
        this.handlers = handlers;
        this.onNaturalClose = onNaturalClose;
        this.startTime = startTime;
    }

    /**
     * Opens the repository and starts consuming events asynchronously in a background thread.
     *
     * @throws IOException if the repository cannot be opened
     */
    public void start() throws IOException {
        LOG.debug("Starting JFR streaming consumer: sessionId={} path={}", sessionId, streamingRepoPath);

        this.eventStream = EventStream.openRepository(streamingRepoPath);

        if (startTime != null) {
            eventStream.setStartTime(startTime);
        }

        for (JfrStreamingHandler handler : handlers) {
            handler.initialize(this::closeStream);
        }

        for (JfrStreamingHandler handler : handlers) {
            eventStream.onEvent(handler.eventType(), handler::onEvent);
        }

        eventStream.onError(throwable ->
                LOG.error("Error in JFR streaming consumer: sessionId={}", sessionId, throwable));

        eventStream.onClose(() -> {
            if (!closedExplicitly.get()) {
                LOG.info("JFR stream ended naturally (JVM shutdown): sessionId={}", sessionId);
                for (JfrStreamingHandler handler : handlers) {
                    handler.close();
                }
                if (onNaturalClose != null) {
                    onNaturalClose.run();
                }
            }
        });

        Schedulers.streamingExecutor()
                .execute(() -> eventStream.start());
    }

    /**
     * Closes the event stream without marking as explicitly closed.
     * This triggers the {@code onClose} callback which handles natural close logic.
     */
    public void closeStream() {
        if (eventStream != null) {
            eventStream.close();
        }
    }

    @Override
    public void close() {
        LOG.debug("Stopping JFR streaming consumer: sessionId={}", sessionId);
        closedExplicitly.set(true);
        for (JfrStreamingHandler handler : handlers) {
            handler.close();
        }
        if (eventStream != null) {
            eventStream.close();
        }
    }
}
