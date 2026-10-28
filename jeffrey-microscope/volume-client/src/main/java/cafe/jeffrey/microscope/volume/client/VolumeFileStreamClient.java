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

import cafe.jeffrey.microscope.remote.client.FileStreamClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.volume.VolumeAccess.OpenedFile;
import cafe.jeffrey.shared.volume.layout.FileRefusal;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.SessionFile;
import cafe.jeffrey.shared.volume.layout.VolumeSession;
import cafe.jeffrey.shared.volume.recording.RecordingFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * One file of a session off the volume, refused in {@code FileRefusal}'s words: a file the session
 * does not hold, an empty recording, and one gone by the time it is opened — looked up once more first, since the hub's compression job may have just
 * replaced a recording with its archive under the same id.
 */
final class VolumeFileStreamClient implements FileStreamClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeFileStreamClient.class);

    private final VolumeSource source;

    VolumeFileStreamClient(VolumeSource source) {
        this.source = source;
    }

    @Override
    public void streamFile(ProjectScope scope, String sessionId, String fileId, InputStreamConsumer consumer) {
        OpenedFile opened = source.read(() -> open(scope, sessionId, fileId)
                .or(() -> {
                    LOG.debug("File was replaced before it could be opened, looking it up again: "
                            + "session_id={} file_id={}", sessionId, fileId);
                    return open(scope, sessionId, fileId);
                })
                .orElseThrow(() -> Exceptions.invalidRequest(FileRefusal.VANISHED.reason(fileId, sessionId))));

        try (opened) {
            consumer.accept(opened.content(), new TransferredFile(opened.name(), opened.size()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot stream a file off the recording volume: file=" + opened.name(), e);
        }
    }

    /** The file opened, refused with its reason, or empty when it is gone by the time it is opened. */
    private Optional<OpenedFile> open(ProjectScope scope, String sessionId, String fileId) {
        ProjectTree project = source.resolvedProject(scope);
        VolumeSession session = source.sessions(project).session(sessionId)
                .orElseThrow(() -> Exceptions.recordingSessionNotFound(sessionId));
        List<SessionFile> files = project.files(session);

        SessionFile file = files.stream()
                .filter(candidate -> candidate.id().equals(fileId))
                .reduce(RecordingFormat.theOneThatStays(SessionFile::name))
                .orElseThrow(() -> Exceptions.invalidRequest(FileRefusal.NOT_IN_SESSION.reason(sessionId, fileId)));

        if (file.recording() && file.size() == 0) {
            throw Exceptions.invalidRequest(FileRefusal.EMPTY_RECORDING.reason(file.name()));
        }
        return source.volume().open(file.path());
    }
}
