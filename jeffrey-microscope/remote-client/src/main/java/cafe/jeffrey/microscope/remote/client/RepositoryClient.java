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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;

import java.util.List;

/**
 * A project's recording sessions: listed, read one at a time with their files, and — on a source
 * that allows it — deleted or pinned.
 */
public interface RepositoryClient {

    default List<RecordingSessionResponse> recordingSessions(ProjectScope scope) {
        return recordingSessions(scope, RecordingSessionFilter.ALL);
    }

    /**
     * The sessions of a project that satisfy the filter, newest first, each with its file count and
     * size but not its files.
     */
    List<RecordingSessionResponse> recordingSessions(ProjectScope scope, RecordingSessionFilter filter);

    /** One session with its files — the only call that carries files. */
    RecordingSessionResponse recordingSession(ProjectScope scope, String sessionId);

    void deleteSession(ProjectScope scope, String sessionId);
}
