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

package cafe.jeffrey.microscope.core.mcp.tools.remote;

import cafe.jeffrey.microscope.remote.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Turns a {@link RemoteSessionRef} back into the remote, project and session it names, and turns every
 * way that can fail into one sentence the model can act on: the ref went stale, ask for a fresh one.
 * <p>
 * Shared by the tools that take a sessionRef — download, per-file fetch — so that a
 * session that has been retired by retention reads the same whichever tool bumped into it. The
 * failure that actually happened is logged rather than surfaced, because a remote that is merely
 * unreachable is indistinguishable, in the model's answer, from one that was disconnected on
 * purpose, and the logs are where that distinction is worth keeping.
 */
public final class RemoteSessionLocator {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteSessionLocator.class);

    private final ProjectManagerResolver resolver;

    public RemoteSessionLocator(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    /** The configured remote's address identity, which its downloads' {@code origin.*} tags carry. */
    public Optional<String> currentAddress() {
        return resolver.currentRemote().map(remote -> remote.info().address().identity());
    }

    public RemoteInfo remoteInfo(RemoteSessionRef ref) {
        try {
            return resolver.resolveRemote().info();
        } catch (JeffreyException e) {
            LOG.debug("No remote is configured for a session_ref: reason={}", e.getMessage(), e);
            throw staleRef(ref, "no remote is configured for this Jeffrey any more");
        }
    }

    public ProjectManager project(RemoteSessionRef ref) {
        try {
            return resolver.resolve(ref.workspaceId(), ref.projectId());
        } catch (StatusRuntimeException e) {
            LOG.debug("Workspace or project lookup failed for a session_ref: workspace_id={} reason={}",
                    ref.workspaceId(), e.getMessage(), e);
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw staleRef(ref, "its workspace or project is no longer there");
            }
            throw GrpcClientErrors.toJeffreyException(e);
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
     * The session as the remote holds it now, files included.
     */
    public RecordingSession session(ProjectManager project, RemoteSessionRef ref, RemoteInfo remote) {
        try {
            return project.repositoryManager().recordingSession(ref.sessionId());
        } catch (StatusRuntimeException e) {
            LOG.debug("Session lookup failed on the remote: session_id={} reason={}",
                    ref.sessionId(), e.getMessage(), e);
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw staleRef(ref, retired(remote));
            }
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (JeffreyException e) {
            LOG.debug("Session lookup failed on the remote: session_id={} reason={}",
                    ref.sessionId(), e.getMessage(), e);
            if (e.getCode() == ErrorCode.REMOTE_UNAVAILABLE || e.getCode() == ErrorCode.REMOTE_OPERATION_FAILED) {
                throw e;
            }
            throw staleRef(ref, retired(remote));
        }
    }

    private static String retired(RemoteInfo remote) {
        return "the remote " + remote.name() + " no longer has it, which usually means retention removed it";
    }

    public static IllegalArgumentException staleRef(RemoteSessionRef ref, String why) {
        return new IllegalArgumentException(
                "Session " + ref.sessionId() + " cannot be reached: " + why
                        + ". Call remote_sessions again for a current sessionRef.");
    }
}
