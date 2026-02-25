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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.jfr.JfrMessageEmitter;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.provider.platform.repository.AlertRepository;
import pbouda.jeffrey.provider.platform.repository.MessageRepository;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of JFR streaming consumers for all active sessions.
 * Tracks active consumers and provides methods to register/unregister them
 * based on session lifecycle events.
 */
public class JfrStreamingConsumerManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(JfrStreamingConsumerManager.class);

    private static final Duration REPLAY_BUFFER = Duration.ofSeconds(10);

    private final ConcurrentHashMap<String, JfrStreamingConsumer> consumers = new ConcurrentHashMap<>();
    private final JeffreyDirs jeffreyDirs;
    private final SessionFinishEventEmitter eventEmitter;
    private final PlatformRepositories platformRepositories;
    private final Clock clock;
    private final Duration heartbeatTimeout;
    private final Duration heartbeatStartupTimeout;
    private final boolean requireInitialHeartbeat;

    public JfrStreamingConsumerManager(
            JeffreyDirs jeffreyDirs,
            SessionFinishEventEmitter eventEmitter,
            PlatformRepositories platformRepositories,
            Clock clock,
            Duration heartbeatTimeout,
            Duration heartbeatStartupTimeout,
            boolean requireInitialHeartbeat) {

        this.jeffreyDirs = jeffreyDirs;
        this.eventEmitter = eventEmitter;
        this.platformRepositories = platformRepositories;
        this.clock = clock;
        this.heartbeatTimeout = heartbeatTimeout;
        this.heartbeatStartupTimeout = heartbeatStartupTimeout;
        this.requireInitialHeartbeat = requireInitialHeartbeat;
    }

    /**
     * Registers a new streaming consumer for the given session.
     * Only registers if no consumer already exists for this session.
     *
     * @param repositoryInfo       information about the repository
     * @param sessionInfo          information about the session
     * @param repositoryRepository project repository for persisting heartbeat data
     * @param projectInfo          project that owns this session (used for session-finished events)
     */
    public void registerConsumer(
            RepositoryInfo repositoryInfo,
            ProjectInstanceSessionInfo sessionInfo,
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo) {

        String sessionId = sessionInfo.sessionId();
        if (consumers.containsKey(sessionId)) {
            LOG.debug("Consumer already registered: sessionId={}", sessionId);
            return;
        }

        Path sessionPath = SessionPaths.resolve(jeffreyDirs, repositoryInfo, sessionInfo);
        Path streamingRepoPath = sessionPath.resolve("streaming-repo");
        if (!Files.isDirectory(streamingRepoPath)) {
            LOG.warn("Streaming repo directory does not exist, marking session finished: sessionId={} path={}",
                    sessionId, streamingRepoPath);
            repositoryRepository.markSessionFinished(sessionId, clock.instant());
            return;
        }

        MessageRepository messageRepository =
                platformRepositories.newMessageRepository(projectInfo.id());
        AlertRepository alertRepository =
                platformRepositories.newAlertRepository(projectInfo.id());

        List<JfrStreamingHandler> handlers = List.of(
                new JfrHeartbeatHandler(sessionId, repositoryRepository, clock, heartbeatTimeout, heartbeatStartupTimeout, requireInitialHeartbeat),
                new MessageStreamingHandler(sessionId, messageRepository),
                new AlertStreamingHandler(sessionId, alertRepository)
        );

        Runnable onNaturalClose = () -> {
            LOG.info("Stream closed naturally, marking session finished: sessionId={} projectId={}",
                    sessionId, projectInfo.id());
            try {
                repositoryRepository.markSessionFinished(sessionId, clock.instant());
                eventEmitter.emitSessionFinished(projectInfo, sessionInfo, WorkspaceEventCreator.STREAMING_CONSUMER);
                JfrMessageEmitter.sessionFinished(sessionId, projectInfo.id());
            } catch (Exception e) {
                LOG.error("Failed to process natural stream close: sessionId={} projectId={}",
                        sessionId, projectInfo.id(), e);
            } finally {
                consumers.remove(sessionId);
            }
        };

        // Resume from last heartbeat - 10s on restart (null for new sessions → start from beginning)
        Instant startTime = sessionInfo.lastHeartbeatAt() != null
                ? sessionInfo.lastHeartbeatAt().minus(REPLAY_BUFFER)
                : null;

        JfrStreamingConsumer consumer = new JfrStreamingConsumer(
                sessionId, sessionPath, handlers, onNaturalClose, startTime);

        try {
            consumer.start();
        } catch (IOException e) {
            LOG.error("Failed to start JFR streaming consumer: sessionId={} path={}", sessionId, sessionPath, e);
            return;
        }

        JfrStreamingConsumer existing = consumers.putIfAbsent(sessionId, consumer);
        if (existing != null) {
            // Another thread won the race — close the consumer we just created
            consumer.close();
            LOG.debug("Consumer already registered by another thread, closing duplicate: sessionId={}", sessionId);
        } else {
            LOG.info("Registered JFR streaming consumer: sessionId={}", sessionId);
        }
    }

    /**
     * Unregisters and closes the streaming consumer for the given session.
     *
     * @param sessionId the session ID to unregister
     */
    public void unregisterConsumer(String sessionId) {
        JfrStreamingConsumer consumer = consumers.remove(sessionId);
        if (consumer != null) {
            consumer.close();
            LOG.info("Unregistered JFR streaming consumer: sessionId={}", sessionId);
        }
    }

    @Override
    public void close() {
        LOG.info("Closing all JFR streaming consumers: count={}", consumers.size());
        consumers.values().forEach(JfrStreamingConsumer::close);
        consumers.clear();
    }
}
