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
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.EventTypeName;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Consumes JFR events from a streaming repository for a specific session.
 * Uses {@link EventStream#openRepository(Path)} to open a live JFR repository
 * and processes custom application events (jeffrey.ImportantMessage).
 */
public class JfrStreamingConsumer implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(JfrStreamingConsumer.class);

    private static final String STREAMING_REPO_DIR = "streaming-repo";

    private final String sessionId;
    private final Path streamingRepoPath;
    private final JfrHeartbeatHandler heartbeatHandler;

    private EventStream eventStream;

    public JfrStreamingConsumer(String sessionId, Path sessionPath, JfrHeartbeatHandler heartbeatHandler) {
        this.sessionId = sessionId;
        this.streamingRepoPath = sessionPath.resolve(STREAMING_REPO_DIR);
        this.heartbeatHandler = heartbeatHandler;
    }

    /**
     * Opens the repository and starts consuming events asynchronously in a background thread.
     *
     * @throws IOException if the repository cannot be opened
     */
    public void start() throws IOException {
        LOG.debug("Starting JFR streaming consumer: sessionId={} path={}", sessionId, streamingRepoPath);

        this.eventStream = EventStream.openRepository(streamingRepoPath);

        // Register event handlers for custom application events
        eventStream.onEvent(EventTypeName.IMPORTANT_MESSAGE, this::handleMessage);
        eventStream.onEvent(EventTypeName.HEARTBEAT, heartbeatHandler::onEvent);

        // Log errors from the stream
        eventStream.onError(throwable ->
                LOG.error("Error in JFR streaming consumer: sessionId={}", sessionId, throwable));

        eventStream.startAsync();
    }

    private void handleMessage(RecordedEvent event) {
        String message = event.getString("message");
        String level = event.hasField("level") ? event.getString("level") : "INFO";
        LOG.debug("[JFR] ImportantMessage: sessionId={} level={} message={}", sessionId, level, message);
    }

    public String sessionId() {
        return sessionId;
    }

    public java.time.Instant lastHeartbeat() {
        return heartbeatHandler != null ? heartbeatHandler.lastHeartbeat() : null;
    }

    @Override
    public void close() {
        LOG.debug("Stopping JFR streaming consumer: sessionId={}", sessionId);
        if (heartbeatHandler != null) {
            heartbeatHandler.flush();
        }
        if (eventStream != null) {
            eventStream.close();
        }
    }
}
