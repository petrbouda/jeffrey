/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools.connector;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Turns a {@link ConnectorSessionRef} back into the connector, project and session it names, and turns every
 * way that can fail into one sentence the model can act on: the ref went stale, ask for a fresh one.
 * <p>
 * Shared by the tools that take a sessionRef — download, per-file fetch — so that a
 * session that has been retired by retention reads the same whichever tool bumped into it. The
 * failure that actually happened is logged rather than surfaced, because the model can act on
 * either the same way — ask for a fresh ref — and the logs are where the cause is worth keeping.
 */
public final class ConnectorSessionLocator {

    private static final String NOT_CONFIGURED = "Session %s cannot be reached: no connector is configured for this "
            + "Jeffrey. Call connector_status, which says how to declare one.";

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorSessionLocator.class);

    private final ProjectManagerResolver resolver;

    public ConnectorSessionLocator(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    /** The configured connector's address identity, which its downloads' {@code origin.*} tags carry. */
    public Optional<String> currentAddress() {
        return resolver.currentConnector().map(connector -> connector.info().address().identity());
    }

    public ConnectorInfo connectorInfo(ConnectorSessionRef ref) {
        try {
            return resolver.resolveConnector().info();
        } catch (JeffreyException e) {
            LOG.debug("No connector is configured for a session_ref: reason={}", e.getMessage(), e);
            throw new IllegalArgumentException(NOT_CONFIGURED.formatted(ref.sessionId()));
        }
    }

    public ProjectManager project(ConnectorSessionRef ref) {
        try {
            return resolver.resolve(ref.workspaceId(), ref.projectId());
        } catch (JeffreyException e) {
            LOG.debug("Workspace or project lookup failed for a session_ref: workspace_id={} reason={}",
                    ref.workspaceId(), e.getMessage(), e);
            if (e.getCode().isNotFound()) {
                throw staleRef(ref, "its workspace or project is no longer there");
            }
            throw e;
        }
    }

    /**
     * The session as the connector holds it now, files included.
     */
    public RecordingSession session(ProjectManager project, ConnectorSessionRef ref, ConnectorInfo connector) {
        try {
            return project.repositoryManager().recordingSession(ref.sessionId());
        } catch (JeffreyException e) {
            LOG.debug("Session lookup failed on the connector: session_id={} reason={}",
                    ref.sessionId(), e.getMessage(), e);
            if (e.getCode() == ErrorCode.CONNECTOR_UNAVAILABLE || e.getCode() == ErrorCode.CONNECTOR_OPERATION_FAILED) {
                throw e;
            }
            throw staleRef(ref, retired(connector));
        }
    }

    private static String retired(ConnectorInfo connector) {
        return "the connector " + connector.name() + " no longer has it, which usually means retention removed it";
    }

    public static IllegalArgumentException staleRef(ConnectorSessionRef ref, String why) {
        return new IllegalArgumentException(
                "Session " + ref.sessionId() + " cannot be reached: " + why
                        + ". Call connector_sessions again for a current sessionRef.");
    }
}
