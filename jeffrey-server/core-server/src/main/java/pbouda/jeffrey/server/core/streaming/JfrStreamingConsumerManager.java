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
import pbouda.jeffrey.server.persistence.repository.AlertRepository;
import pbouda.jeffrey.server.persistence.repository.MessageRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.server.persistence.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.server.core.ServerJeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;

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
    private final ServerJeffreyDirs jeffreyDirs;
    private final ServerPlatformRepositories platformRepositories;
    private final Clock clock;
    private final FileHeartbeatReader fileHeartbeatReader;
    private final boolean globalStreamingEnabled;

    public JfrStreamingConsumerManager(
            ServerJeffreyDirs jeffreyDirs,
            ServerPlatformRepositories platformRepositories,
            Clock clock,
            FileHeartbeatReader fileHeartbeatReader,
            boolean globalStreamingEnabled) {

        this.jeffreyDirs = jeffreyDirs;
        this.platformRepositories = platformRepositories;
        this.clock = clock;
        this.fileHeartbeatReader = fileHeartbeatReader;
        this.globalStreamingEnabled = globalStreamingEnabled;
    }

    /**
     * Registers a new streaming consumer for the given session.
     * Only registers if no consumer already exists for this session,
     * and if streaming is enabled according to the three-tier hierarchy
     * (global > workspace > project).
     *
     * @param repositoryInfo          information about the repository
     * @param sessionInfo             information about the session
     * @param repositoryRepository    project repository for persisting heartbeat data
     * @param projectInfo             project that owns this session (used for session-finished events)
     * @param workspaceStreamingEnabled workspace-level streaming override (null = inherit)
     */
    public void registerConsumer(
            RepositoryInfo repositoryInfo,
            ProjectInstanceSessionInfo sessionInfo,
            ProjectRepositoryRepository repositoryRepository,
            ProjectInfo projectInfo,
            Boolean workspaceStreamingEnabled) {

        String sessionId = sessionInfo.sessionId();

        boolean effectiveEnabled = StreamingEnabledResolver.resolve(
                globalStreamingEnabled,
                workspaceStreamingEnabled,
                projectInfo.streamingEnabled());

        if (!effectiveEnabled) {
            LOG.info("Streaming disabled, skipping consumer registration: sessionId={} projectId={}",
                    sessionId, projectInfo.id());
            return;
        }

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
                new MessageStreamingHandler(messageRepository),
                new AlertStreamingHandler(alertRepository)
        );

        Runnable onNaturalClose = () -> {
            if (consumers.remove(sessionId) == null) {
                LOG.debug("Stream closed but consumer already unregistered: sessionId={}", sessionId);
                return;
            }
            LOG.info("Streaming closed naturally: sessionId={} projectId={}", sessionId, projectInfo.id());
        };

        // Resume from last heartbeat file - 10s on restart (null for new sessions → start from beginning)
        Instant startTime = fileHeartbeatReader.readLastHeartbeat(sessionPath)
                .map(hb -> hb.minus(REPLAY_BUFFER))
                .orElse(null);

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
