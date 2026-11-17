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

package cafe.jeffrey.volumescheduler.storage;

import cafe.jeffrey.volumescheduler.volume.ProjectRef;
import cafe.jeffrey.volumescheduler.volume.RecordingSession;
import cafe.jeffrey.volumescheduler.volume.RecordingSessionFile;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What one project occupies on the volume: the bytes and the file count over every session, and
 * the newest file's timestamp. The volume scheduler does not read the files, so there is
 * deliberately no breakdown by kind.
 *
 * @param lastActivityAt the newest file's timestamp; empty for a project with no file
 */
public record ProjectStorage(ProjectRef project, long totalBytes, int totalFiles, Optional<Instant> lastActivityAt) {

    /** Measures a project from its sessions. */
    public static ProjectStorage measure(ProjectRef project, List<RecordingSession> sessions) {
        List<RecordingSessionFile> files = sessions.stream()
                .flatMap(session -> session.files().stream())
                .toList();
        long totalBytes = files.stream()
                .mapToLong(RecordingSessionFile::size)
                .sum();
        Optional<Instant> lastActivity = files.stream()
                .map(RecordingSessionFile::createdAt)
                .max(Comparator.naturalOrder());
        return new ProjectStorage(project, totalBytes, files.size(), lastActivity);
    }
}
