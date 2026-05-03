/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
