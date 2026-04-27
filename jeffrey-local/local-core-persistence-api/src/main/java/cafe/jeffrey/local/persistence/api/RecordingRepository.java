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

package cafe.jeffrey.local.persistence.api;

import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository {

    /**
     * Finds a recording by its ID.
     *
     * @return a single recording
     */
    Optional<Recording> findRecording(String recordingId);

    /**
     * Deletes a recording by its ID.
     *
     * @param recordingId the ID of the recording to delete
     */
    void deleteRecordingWithFiles(String recordingId);

    /**
     * Finds all recordings in the project.
     *
     * @return a list of recordings
     */
    List<Recording> findAllRecordings();

    /**
     * Creates a new group in the project.
     *
     * @param groupName the name of the group
     */
    String insertGroup(String groupName);

    /**
     * Deletes a group by its ID.
     *
     * @param groupId the ID of the group to delete
     */
    void deleteGroup(String groupId);

    /**
     * Finds all recording groups in the project.
     *
     * @return a list of recording groups
     */
    List<RecordingGroup> findAllRecordingGroups();

    /**
     * Finds a recording by its ID.
     *
     * @param recordingId the ID of the recording to find
     * @return a single recording if it exists in the project otherwise an empty optional
     */
    Optional<Recording> findById(String recordingId);

    /**
     * Inserts a new recording into the project with a main recording file.
     *
     * @param recording the recording to insert.
     */
    void insertRecording(Recording recording, RecordingFile recordingFile);

    /**
     * Inserts a new recording file into the project and recording folder.
     *
     * @param recordingFile the recording file to insert.
     */
    void insertRecordingFile(RecordingFile recordingFile);

    /**
     * Checks if the group exists.
     *
     * @param groupId the ID of the group to check
     * @return true if the group exists, false otherwise
     */
    boolean groupExists(String groupId);

    /**
     * Moves a recording to a different group, or to ungrouped if groupId is null.
     *
     * @param recordingId the ID of the recording to move
     * @param groupId     the target group ID, or null to ungroup
     */
    void updateRecordingGroup(String recordingId, String groupId);

    /**
     * Finds a group by its ID.
     *
     * @param groupId the ID of the group to find
     * @return the group if it exists, otherwise empty
     */
    Optional<RecordingGroup> findGroupById(String groupId);

    /**
     * Finds all recordings belonging to a specific group.
     *
     * @param groupId the ID of the group
     * @return a list of recordings in the group
     */
    List<Recording> findRecordingsByGroupId(String groupId);
}
