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
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of JFR streaming consumers for all active sessions.
 * Tracks active consumers and provides methods to register/unregister them
 * based on session lifecycle events.
 */
public class JfrStreamingConsumerManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(JfrStreamingConsumerManager.class);

    private final ConcurrentHashMap<String, JfrStreamingConsumer> consumers = new ConcurrentHashMap<>();
    private final JeffreyDirs jeffreyDirs;

    public JfrStreamingConsumerManager(JeffreyDirs jeffreyDirs) {
        this.jeffreyDirs = jeffreyDirs;
    }

    /**
     * Registers a new streaming consumer for the given session.
     * Only registers if streaming is enabled for the session and no consumer already exists.
     *
     * @param repositoryInfo information about the repository
     * @param sessionInfo    information about the session
     */
    public void registerConsumer(RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        if (!sessionInfo.streamingEnabled()) {
            LOG.debug("Streaming not enabled for session, skipping: sessionId={}", sessionInfo.sessionId());
            return;
        }

        String sessionId = sessionInfo.sessionId();
        if (consumers.containsKey(sessionId)) {
            LOG.debug("Consumer already registered: sessionId={}", sessionId);
            return;
        }

        Path sessionPath = resolveSessionPath(repositoryInfo, sessionInfo);
        JfrStreamingConsumer consumer = new JfrStreamingConsumer(sessionId, sessionPath);

        try {
            consumer.start();
            consumers.put(sessionId, consumer);
            LOG.info("Registered JFR streaming consumer: sessionId={}", sessionId);
        } catch (IOException e) {
            LOG.error("Failed to start JFR streaming consumer: sessionId={} path={}", sessionId, sessionPath, e);
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

    /**
     * Returns an unmodifiable set of currently active consumer session IDs.
     *
     * @return set of active session IDs
     */
    public Set<String> getActiveConsumerIds() {
        return Collections.unmodifiableSet(consumers.keySet());
    }

    /**
     * Returns the number of active consumers.
     *
     * @return count of active consumers
     */
    public int getActiveConsumerCount() {
        return consumers.size();
    }

    private Path resolveSessionPath(RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null
                ? jeffreyDirs.workspaces()
                : Path.of(workspacesPath);
        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath())
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }

    @Override
    public void close() {
        LOG.info("Closing all JFR streaming consumers: count={}", consumers.size());
        consumers.values().forEach(JfrStreamingConsumer::close);
        consumers.clear();
    }
}
