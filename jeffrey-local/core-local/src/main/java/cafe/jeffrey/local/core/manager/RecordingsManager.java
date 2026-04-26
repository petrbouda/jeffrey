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

package cafe.jeffrey.local.core.manager;

import cafe.jeffrey.local.persistence.model.RecordingGroup;
import cafe.jeffrey.shared.common.model.Recording;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface RecordingsManager {

    List<Recording> all();

    void createGroup(String groupName);

    List<RecordingGroup> allRecordingGroups();

    void deleteGroup(String groupId);

    void delete(String recordingId);

    void moveRecordingToGroup(String recordingId, String groupId);

    Optional<Path> findRecordingFile(String recordingId, String fileId);
}
