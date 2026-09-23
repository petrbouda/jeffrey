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

package cafe.jeffrey.hub.model.repository;

import java.util.List;

/**
 * Aggregated storage statistics for a single project instance.
 * Computed by walking the instance's session directories on disk.
 */
public record InstanceStats(int fileCount, long totalSizeBytes) {

    /** Over sessions loaded with their files; headers alone would count every session as empty. */
    public static InstanceStats of(List<RecordingSession> sessions) {
        int fileCount = sessions.stream().mapToInt(session -> session.files().size()).sum();
        long totalSize = sessions.stream().mapToLong(RecordingSession::totalSizeBytes).sum();
        return new InstanceStats(fileCount, totalSize);
    }
}
