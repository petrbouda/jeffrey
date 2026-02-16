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

import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;

import java.time.Clock;
import java.time.Instant;

/**
 * Handles heartbeat JFR events for a specific session.
 * Tracks the last heartbeat timestamp in memory and periodically persists it
 * to the database to avoid per-heartbeat writes.
 */
public class JfrHeartbeatHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JfrHeartbeatHandler.class);

    private static final long PERSIST_INTERVAL_SECONDS = 30;

    private final String sessionId;
    private final ProjectRepositoryRepository repository;
    private final Clock clock;
    private volatile Instant lastHeartbeat;
    private volatile Instant lastPersistedAt;

    public JfrHeartbeatHandler(String sessionId, ProjectRepositoryRepository repository, Clock clock) {
        this.sessionId = sessionId;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Event handler to register with {@code EventStream.onEvent(HEARTBEAT, handler::onEvent)}.
     */
    public void onEvent(RecordedEvent event) {
        lastHeartbeat = event.getEndTime();
        if (shouldPersist()) {
            repository.updateLastHeartbeat(sessionId, lastHeartbeat);
            lastPersistedAt = clock.instant();
            LOG.debug("Persisted heartbeat: sessionId={} lastHeartbeat={}", sessionId, lastHeartbeat);
        }
    }

    public Instant lastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Force-persist current state (called on consumer shutdown).
     */
    public void flush() {
        if (lastHeartbeat != null) {
            repository.updateLastHeartbeat(sessionId, lastHeartbeat);
            LOG.debug("Flushed heartbeat on shutdown: sessionId={} lastHeartbeat={}", sessionId, lastHeartbeat);
        }
    }

    private boolean shouldPersist() {
        return lastPersistedAt == null
                || clock.instant().isAfter(lastPersistedAt.plusSeconds(PERSIST_INTERVAL_SECONDS));
    }
}
