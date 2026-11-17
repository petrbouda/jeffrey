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

import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;

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
     * Finds the recording holding exactly one file with this name and size, the shape a single
     * imported or uploaded file is stored in. When several match, one that already has a profile
     * is preferred, and the newest wins within the recordings with a profile and within those without.
     *
     * @param fileName    the file's own name, without any directory
     * @param sizeInBytes the file's size in bytes
     * @return the preferred one-file recording of a file with this name and size, otherwise empty
     */
    Optional<Recording> findByFileNameAndSize(String fileName, long sizeInBytes);

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

    // --- Bundles ---

    /**
     * Inserts a bundle with everything it holds in one transaction: the bundle row, its download-only
     * files, and each member recording with its files. A crash midway leaves no bundle at all rather
     * than one missing its recording.
     *
     * @param bundle  the bundle, carrying its own download-only files
     * @param members the bundle's recordings, each with its files and {@link Recording#bundleId()} set
     */
    void insertBundle(Bundle bundle, List<Recording> members);

    /** Creates a bundle around a standalone recording without changing its files or profiles. */
    void attachRecordingToBundle(String recordingId, Bundle bundle);

    /** Inserts a download-only file, or refreshes its metadata while keeping its id. */
    void upsertBundleFile(BundleFile file);

    /**
     * @return every bundle with its download-only files; members are listed with the recordings
     */
    List<Bundle> findAllBundles();

    Optional<Bundle> findBundle(String bundleId);

    /**
     * @return the bundle's member recordings with their files
     */
    List<Recording> findBundleMembers(String bundleId);

    /**
     * Deletes the bundle row and its download-only file rows. Members are deleted by the caller,
     * which owns their profiles and files.
     */
    void deleteBundle(String bundleId);

    void updateBundleGroup(String bundleId, String groupId);

    /**
     * @return the bundles sitting in a group
     */
    List<Bundle> findBundlesByGroupId(String groupId);
}
