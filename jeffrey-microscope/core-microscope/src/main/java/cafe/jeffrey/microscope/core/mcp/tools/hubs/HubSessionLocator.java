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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.hub.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a {@link HubSessionRef} back into the hub, project and session it names, and turns every
 * way that can fail into one sentence the model can act on: the ref went stale, ask for a fresh one.
 * <p>
 * Shared by the tools that take a session_ref — download, per-file fetch — so that a
 * session that has been retired by retention reads the same whichever tool bumped into it. The
 * failure that actually happened is logged rather than surfaced, because a hub that is merely
 * unreachable is indistinguishable, in the model's answer, from one that was disconnected on
 * purpose, and the logs are where that distinction is worth keeping.
 */
public final class HubSessionLocator {

    private static final Logger LOG = LoggerFactory.getLogger(HubSessionLocator.class);

    private final ProjectManagerResolver resolver;

    public HubSessionLocator(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    public HubInfo hubInfo(HubSessionRef ref) {
        try {
            return resolver.resolveHub(ref.hubId()).info();
        } catch (JeffreyException e) {
            LOG.debug("Hub lookup failed for a session_ref: hub_id={} reason={}", ref.hubId(), e.getMessage(), e);
            throw staleRef(ref, "its hub is no longer connected to this Jeffrey");
        }
    }

    public ProjectManager project(HubSessionRef ref) {
        try {
            return resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId()).projectManager();
        } catch (StatusRuntimeException e) {
            LOG.debug("Workspace or project lookup failed for a session_ref: hub_id={} reason={}",
                    ref.hubId(), e.getMessage(), e);
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw staleRef(ref, "its workspace or project is no longer there");
            }
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (JeffreyException e) {
            LOG.debug("Workspace or project lookup failed for a session_ref: hub_id={} reason={}",
                    ref.hubId(), e.getMessage(), e);
            if (e.getCode().isNotFound()) {
                throw staleRef(ref, "its workspace or project is no longer there");
            }
            throw e;
        }
    }

    /**
     * The session as the hub holds it now, files included.
     */
    public RecordingSession session(ProjectManager project, HubSessionRef ref, HubInfo hubInfo) {
        try {
            return project.repositoryManager().recordingSession(ref.sessionId());
        } catch (StatusRuntimeException e) {
            LOG.debug("Session lookup failed on the hub: hub_id={} session_id={} reason={}",
                    ref.hubId(), ref.sessionId(), e.getMessage(), e);
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw staleRef(ref, retired(hubInfo));
            }
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (JeffreyException e) {
            LOG.debug("Session lookup failed on the hub: hub_id={} session_id={} reason={}",
                    ref.hubId(), ref.sessionId(), e.getMessage(), e);
            if (e.getCode() == ErrorCode.HUB_UNAVAILABLE || e.getCode() == ErrorCode.REMOTE_OPERATION_FAILED) {
                throw e;
            }
            throw staleRef(ref, retired(hubInfo));
        }
    }

    private static String retired(HubInfo hubInfo) {
        return "hub " + hubInfo.name() + " no longer has it, which usually means retention removed it";
    }

    public static IllegalArgumentException staleRef(HubSessionRef ref, String why) {
        return new IllegalArgumentException(
                "Session " + ref.sessionId() + " cannot be reached: " + why
                        + ". Call hubs_sessions again for a current session_ref.");
    }
}
