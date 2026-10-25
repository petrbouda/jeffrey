/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.hub.client.ProjectScope;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeSession;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A project's sessions off the volume. A listing is filtered on the sessions alone, then reads the
 * files of the ones it keeps for their count and size — as the hub does, so a narrow filter over a
 * long history lists only a few directories.
 */
final class VolumeRepositoryClient implements RepositoryClient {

    private final VolumeSource source;

    VolumeRepositoryClient(VolumeSource source) {
        this.source = source;
    }

    @Override
    public List<RecordingSessionResponse> recordingSessions(ProjectScope scope, RecordingSessionFilter filter) {
        return source.read(() -> {
            ProjectTree project = source.requireProject(scope);
            List<VolumeSession> sessions = source.sessions(project).all();
            Map<String, VolumeSession> byId = sessions.stream()
                    .collect(Collectors.toMap(VolumeSession::id, Function.identity()));

            List<RecordingSession> kept = filter.apply(sessions.stream().map(VolumeResponses::unlisted).toList());
            return kept.stream()
                    .map(session -> byId.get(session.id()))
                    .map(session -> VolumeResponses.header(session, project.files(session.path(), session.zone())))
                    .toList();
        });
    }

    @Override
    public RecordingSessionResponse recordingSession(ProjectScope scope, String sessionId) {
        return source.read(() -> {
            ProjectTree project = source.requireProject(scope);
            ProjectSessions sessions = source.sessions(project);
            VolumeSession session = sessions.session(sessionId)
                    .orElseThrow(() -> Exceptions.recordingSessionNotFound(sessionId));
            return VolumeResponses.withFiles(session, project.files(session.path(), session.zone()));
        });
    }

    @Override
    public void deleteSession(ProjectScope scope, String sessionId) {
        throw VolumeSource.readOnly("Cannot delete session " + sessionId);
    }

    @Override
    public void setSessionRetained(ProjectScope scope, String sessionId, boolean retained) {
        throw VolumeSource.readOnly("Cannot pin or unpin session " + sessionId);
    }
}
