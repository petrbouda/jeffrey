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

package cafe.jeffrey.shared.common.model.workspace.event;

import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.time.Instant;

/**
 * Content of a session-file-created event, shared by the RECORDING and ARTIFACT event
 * types — the payload shape is identical and the discriminator already lives in the
 * event type. The file's own identifier is carried in
 * {@code WorkspaceEvent.originEventId} rather than here, following the convention of the
 * other content records and because the queue's dedup key is derived from it.
 *
 * <p><b>{@code sizeBytes} is a first-sighting value, not a live file size.</b> It records
 * what the file measured when it was first announced; a later LZ4 compression changes the
 * on-disk size without producing a new event. That is correct for an event log, but this
 * record must not be read as a file inventory.
 */
public record SessionFileCreatedEventContent(
        String instanceId,
        String sessionId,
        String fileName,
        SupportedRecordingFile fileType,
        FileCategory fileCategory,
        long sizeBytes,
        Instant fileCreatedAt) {

    public SessionFileCreatedEventContent {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative: " + sizeBytes);
        }
    }
}
