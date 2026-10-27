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

import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RepositoryClient;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.shared.volume.layout.ProjectSessions;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A project's sessions off the volume. A listing is filtered on the sessions alone, then describes
 * the files of the ones it keeps for their count and size, off the listing each session was read
 * from.
 */
final class VolumeRepositoryClient implements RepositoryClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeRepositoryClient.class);

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

            List<RecordingSession> kept = filter.apply(sessions.stream()
                    .map(session -> VolumeResponses.unlisted(session, project.recordingStarts(session)))
                    .toList());
            return kept.stream()
                    .map(session -> byId.get(session.id()))
                    .map(session -> VolumeResponses.header(session, project.files(session)))
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
            return VolumeResponses.withFiles(session, project.files(session));
        });
    }

    /** Its instance goes too when nothing is left in it ({@link VolumeChanges#deleteSession}). */
    @Override
    public void deleteSession(ProjectScope scope, String sessionId) {
        WritableVolume volume = source.writable("Cannot delete session " + sessionId);
        source.read(() -> {
            SessionDeletion deletion = VolumeChanges.deleteSession(volume, source.requireProject(scope), sessionId);
            if (!deletion.deleted()) {
                throw Exceptions.recordingSessionNotFound(sessionId);
            }
            LOG.info("Deleted session on the recording volume: session_id={} instance_deleted={} source={}",
                    sessionId, deletion.instanceDeleted(), source.description());
        });
    }
}
