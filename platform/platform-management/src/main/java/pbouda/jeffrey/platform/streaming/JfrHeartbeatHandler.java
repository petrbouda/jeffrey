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
import pbouda.jeffrey.shared.common.Schedulers;
import pbouda.jeffrey.shared.common.model.EventTypeName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles heartbeat JFR events for a specific session.
 * Persists the last heartbeat timestamp directly to the database on every event.
 * Owns the heartbeat watchdog that closes the stream when heartbeats stop arriving.
 */
public class JfrHeartbeatHandler implements JfrStreamingHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JfrHeartbeatHandler.class);

    private final String sessionId;
    private final ProjectRepositoryRepository repository;
    private final Clock clock;
    private final Duration heartbeatTimeout;
    private final Duration heartbeatStartupTimeout;
    private final boolean requireInitialHeartbeat;

    private volatile Instant lastHeartbeatAt;
    private ScheduledFuture<?> watchdogFuture;

    public JfrHeartbeatHandler(
            String sessionId,
            ProjectRepositoryRepository repository,
            Clock clock,
            Duration heartbeatTimeout,
            Duration heartbeatStartupTimeout,
            boolean requireInitialHeartbeat) {

        this.sessionId = sessionId;
        this.repository = repository;
        this.clock = clock;
        this.heartbeatTimeout = heartbeatTimeout;
        this.heartbeatStartupTimeout = heartbeatStartupTimeout;
        this.requireInitialHeartbeat = requireInitialHeartbeat;
    }

    @Override
    public String eventType() {
        return EventTypeName.HEARTBEAT;
    }

    @Override
    public void onEvent(RecordedEvent event) {
        LOG.trace("Received Heartbeat: sessionId={} event_timestamp={}", sessionId, event.getStartTime());
        lastHeartbeatAt = event.getStartTime();
        try {
            repository.updateLastHeartbeat(sessionId, event.getStartTime());
        } catch (Exception e) {
            LOG.warn("Failed to persist heartbeat to DB: sessionId={} lastHeartbeat={}",
                    sessionId, event.getStartTime(), e);
        }
        LOG.trace("Persisted heartbeat: sessionId={} lastHeartbeat={}", sessionId, event.getStartTime());
    }

    @Override
    public void initialize(Runnable streamCloser) {
        LOG.info("Heartbeat watchdog started: sessionId={} timeout={} heartbeatStartupTimeout={}",
                sessionId, heartbeatTimeout, heartbeatStartupTimeout);

        long gracePeriodMillis = heartbeatStartupTimeout.toMillis();
        long delayMillis = heartbeatTimeout.toMillis();
        watchdogFuture = Schedulers.watchdogScheduled()
                .scheduleWithFixedDelay(
                        () -> checkHeartbeat(streamCloser), gracePeriodMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void checkHeartbeat(Runnable streamCloser) {
        Instant lastHeartbeat = lastHeartbeatAt;
        Duration elapsed = lastHeartbeat != null ? Duration.between(lastHeartbeat, clock.instant()) : null;
        LOG.trace("Heartbeat check: sessionId={} lastHeartbeatAt={} elapsed={} timeout={}",
                sessionId, lastHeartbeat, elapsed, heartbeatTimeout);
        if (lastHeartbeat != null) {
            if (elapsed.compareTo(heartbeatTimeout) > 0) {
                LOG.info("Heartbeat timeout, closing stream: sessionId={} lastHeartbeat={} elapsed={}",
                        sessionId, lastHeartbeat, elapsed);
                streamCloser.run();
                close();
            }
        } else if (requireInitialHeartbeat) {
            LOG.info("No initial heartbeat received within timeout, closing stream: sessionId={}", sessionId);
            streamCloser.run();
            close();
        }
    }

    @Override
    public void close() {
        if (watchdogFuture != null) {
            watchdogFuture.cancel(false);
        }
    }
}
