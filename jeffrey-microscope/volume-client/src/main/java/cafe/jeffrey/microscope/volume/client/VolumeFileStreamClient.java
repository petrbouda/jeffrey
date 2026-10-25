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
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.volume.VolumeAccess.OpenedFile;
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
 * One file of a session off the volume, refused for the same reasons and in the same words as the
 * hub refuses it: a file the session does not hold, the chunk the profiler is still writing, an
 * empty recording, and one gone by the time it is opened — looked up once more first, since the
 * hub's compression job may have just replaced a recording with its archive under the same id.
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
                .orElseThrow(() -> Exceptions.invalidRequest(
                        "File " + fileId + " was listed for session " + sessionId + " but is no longer on the "
                                + "volume. It may have been replaced by its compressed form; ask for the same file id again.")));

        try (opened) {
            consumer.accept(opened.content(), new TransferredFile(opened.name(), opened.size()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot stream a file off the recording volume: file=" + opened.name(), e);
        }
    }

    /** The file opened, refused with its reason, or empty when it is gone by the time it is opened. */
    private Optional<OpenedFile> open(ProjectScope scope, String sessionId, String fileId) {
        ProjectTree project = source.requireProject(scope);
        VolumeSession session = source.sessions(project).session(sessionId)
                .orElseThrow(() -> Exceptions.recordingSessionNotFound(sessionId));
        List<SessionFile> files = project.files(session.path(), session.zone());

        SessionFile file = files.stream()
                .filter(candidate -> candidate.id().equals(fileId))
                .reduce(VolumeFileStreamClient::theOneThatStays)
                .orElseThrow(() -> Exceptions.invalidRequest("Session " + sessionId + " holds no file with id "
                        + fileId + ". Take the id from the session's file listing."));

        RecordingSession model = VolumeResponses.withModelFiles(session, files);
        if (model.isOpen(VolumeResponses.modelFile(file))) {
            throw Exceptions.invalidRequest("File " + file.name() + " is the chunk the profiler is still writing, "
                    + "and reading it would give a truncated answer. It can be taken once the profiler has rolled the next one.");
        }
        if (file.recording() && file.size() == 0) {
            throw Exceptions.invalidRequest("Recording " + file.name()
                    + " is empty — the profiler stopped before it wrote an event into it.");
        }
        return source.volume().open(file.path());
    }

    /**
     * Which of two files sharing an id to serve: the archive. While the hub's compression job runs
     * a recording and its archive share an id and both are whole; the recording is about to go.
     */
    private static SessionFile theOneThatStays(SessionFile first, SessionFile second) {
        return RecordingFormat.of(first.name()).filter(RecordingFormat::isArchive).isPresent() ? first : second;
    }
}
