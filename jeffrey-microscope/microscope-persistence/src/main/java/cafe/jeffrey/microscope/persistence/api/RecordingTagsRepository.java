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

package cafe.jeffrey.microscope.persistence.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Persistence operations for {@link RecordingTag}s. Tags are key-value metadata attached to
 * a recording. The repository is global (not project-scoped) — recordings live in a single
 * shared QA pool after the unified-recordings migration.
 */
public interface RecordingTagsRepository {

    /**
     * Bulk-insert tags for a recording. Replaces any existing rows with the same
     * {@code (recording_id, tag_key)} key.
     */
    void insert(String recordingId, Map<String, String> tags);

    /**
     * Returns all tags for a single recording.
     */
    List<RecordingTag> listForRecording(String recordingId);

    /**
     * Returns tags for many recordings in a single query, grouped by recording id.
     * Recordings with no tags do not appear in the result map.
     */
    Map<String, List<RecordingTag>> listForRecordings(Collection<String> recordingIds);

    /**
     * Removes all tags belonging to the given recording. Called when a recording is deleted.
     */
    void deleteForRecording(String recordingId);
}
