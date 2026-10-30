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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.persistence.api.*;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.microscope.persistence.api.RecordingGroup;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcRecordingRepository implements RecordingRepository {

    /**
     * A recording can be initialized more than once, each time into a profile of its own. A recording
     * row carries the newest of them, so a caller that wants "the recording's profile" gets the
     * latest initialization and the listing keeps one row per recording.
     */
    //language=sql
    private static final String NEWEST_PROFILE_PER_RECORDING = """
            (SELECT profile_id, profile_name, recording_id,
                    ROW_NUMBER() OVER (PARTITION BY recording_id ORDER BY created_at DESC, profile_id DESC) AS newest_rank
             FROM profiles)""";

    //language=sql
    private static final String SELECT_RECORDINGS_WITH_PROFILE = """
            SELECT r.*, p.profile_id, p.profile_name, (p.profile_id IS NOT NULL) AS has_profile
            FROM recordings r LEFT JOIN """ + NEWEST_PROFILE_PER_RECORDING + """
             p ON p.recording_id = r.id AND p.newest_rank = 1
            WHERE r.""";

    /**
     * Every recording this repository writes belongs to no project, and it reads back only those.
     * Rows with a project id are what an older build left behind when recordings were
     * project-scoped; nothing can open them any more, so they stay out of every listing.
     */
    //language=sql
    private static final String NO_PROJECT = "project_id IS NULL";

    /**
     * The recording made of one file with a given name and size. A recording of several files — the
     * JFR chunks of a downloaded session — never matches, even when one of its files has that name and
     * size. A one-file member of a bundle does: the heap dump a download brought in is the same file a
     * later import of it would be, and handing back its profile is the point. Among
     * several matches one that already has a profile wins, since handing that profile back is the
     * point; newest first within each group.
     */
    //language=sql
    private static final String FIND_RECORDING_ID_BY_FILE_NAME_AND_SIZE =
            "SELECT r.id FROM recordings r LEFT JOIN " + NEWEST_PROFILE_PER_RECORDING
                    + " p ON p.recording_id = r.id AND p.newest_rank = 1 WHERE r." + NO_PROJECT + """
              AND (SELECT count(*) FROM recording_files f WHERE f.recording_id = r.id) = 1
              AND EXISTS (SELECT 1 FROM recording_files f WHERE f.recording_id = r.id AND f.filename = :filename AND f.size_in_bytes = :size_in_bytes)
            ORDER BY (p.profile_id IS NULL), r.created_at DESC LIMIT 1""";

    private static final String PARAM_FILENAME = "filename";
    private static final String PARAM_SIZE_IN_BYTES = "size_in_bytes";

    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcRecordingRepository(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_RECORDINGS);
        this.clock = clock;
    }

    /**
     * A SQL statement together with the {@link StatementLabel} it is reported under
     * in JDBC statement metrics.
     */
    private record LabeledQuery(StatementLabel label, String sql) {
    }

    /**
     * Loads recordings and their files with two queries sharing the same parameters,
     * then attaches the files to their owning recordings. The files query is skipped
     * entirely when the recordings query returns no rows.
     */
    private List<Recording> findRecordingsWithFiles(
            LabeledQuery recordingsQuery, LabeledQuery filesQuery, MapSqlParameterSource params) {

        List<Recording> recordings = databaseClient.query(
                recordingsQuery.label(), recordingsQuery.sql(), params, Mappers.projectRecordingMapper());

        if (recordings.isEmpty()) {
            return recordings;
        }

        List<RecordingFile> recordingFiles = databaseClient.query(
                filesQuery.label(), filesQuery.sql(), params, Mappers.projectRecordingFileMapper());

        Map<String, List<RecordingFile>> filesPerRecording = recordingFiles.stream()
                .collect(Collectors.groupingBy(RecordingFile::recordingId));

        return recordings.stream()
                .map(recording -> {
                    List<RecordingFile> files = filesPerRecording.get(recording.id());
                    return files == null ? recording : recording.withFiles(files);
                })
                .toList();
    }

    // --- Recording operations ---

    /**
     * A recording is several files, and nothing marks one of them as the important one. Without an
     * order the rows come back however the storage feels like returning them, which made "the
     * recording's first file" a different file from one read to the next.
     * <p>
     * Ordering changes nothing about the parse — the files are read independently and the events
     * carry absolute timestamps — only about what a listing shows and which file a caller that
     * wants one gets.
     */
    //language=sql
    private static final String FILES_ORDER = " ORDER BY uploaded_at, filename";

    @Override
    public Optional<Recording> findRecording(String recordingId) {
        String recordingsSql = SELECT_RECORDINGS_WITH_PROFILE + NO_PROJECT + " AND r.id = :recording_id";
        //language=sql
        String filesSql = "SELECT * FROM recording_files WHERE " + NO_PROJECT
                + " AND recording_id = :recording_id" + FILES_ORDER;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        return findRecordingsWithFiles(
                new LabeledQuery(StatementLabel.FIND_RECORDING, recordingsSql),
                new LabeledQuery(StatementLabel.FIND_RECORDING_FILES, filesSql),
                params)
                .stream()
                .findFirst();
    }

    @Override
    public void deleteRecordingWithFiles(String recordingId) {
        // Avoid using 'project_id IS NULL' in DELETE — triggers a DuckDB ART index bug.
        // Recording IDs are UUIDs (globally unique), so the project_id filter is redundant for deletes.
        //language=sql
        String deleteFilesSql = "DELETE FROM recording_files WHERE recording_id = :recording_id";
        //language=sql
        String deleteRecordingSql = "DELETE FROM recordings WHERE id = :recording_id";

        var params = new MapSqlParameterSource().addValue("recording_id", recordingId);

        databaseClient.inTransaction(() -> {
            databaseClient.delete(StatementLabel.DELETE_RECORDING_FILES, deleteFilesSql, params);
            databaseClient.delete(StatementLabel.DELETE_RECORDING, deleteRecordingSql, params);
        });
    }

    @Override
    public List<Recording> findAllRecordings() {
        String recordingsSql = SELECT_RECORDINGS_WITH_PROFILE + NO_PROJECT;
        //language=sql
        String filesSql = "SELECT * FROM recording_files WHERE " + NO_PROJECT + FILES_ORDER;

        return findRecordingsWithFiles(
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDINGS, recordingsSql),
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDING_FILES, filesSql),
                new MapSqlParameterSource());
    }

    @Override
    public Optional<Recording> findById(String recordingId) {
        String sql = SELECT_RECORDINGS_WITH_PROFILE + NO_PROJECT + " AND r.id = :recording_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId);

        return databaseClient.querySingle(
                StatementLabel.FIND_RECORDING, sql, params, Mappers.projectRecordingMapper());
    }

    @Override
    public void insertRecording(Recording recording, RecordingFile recordingFile) {
        //language=SQL
        String sql = """
                INSERT INTO recordings (id, recording_name, group_id, event_source, created_at, recording_started_at, recording_finished_at, bundle_id)
                    VALUES (:id, :recording_name, :group_id, :event_source, :created_at, :recording_started_at, :recording_finished_at, :bundle_id)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", recording.id())
                .addValue("recording_name", recording.recordingName())
                .addValue("group_id", recording.groupId())
                .addValue("event_source", recording.eventSource().name())
                .addValue("created_at", recording.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("recording_started_at", recording.recordingStartedAt() != null
                        ? recording.recordingStartedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("recording_finished_at", recording.recordingFinishedAt() != null
                        ? recording.recordingFinishedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("bundle_id", recording.bundleId());

        databaseClient.inTransaction(() -> {
            databaseClient.insert(StatementLabel.INSERT_RECORDING, sql, params);
            insertRecordingFile(recordingFile);
        });
    }

    @Override
    public Optional<Recording> findByFileNameAndSize(String fileName, long sizeInBytes) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_FILENAME, fileName)
                .addValue(PARAM_SIZE_IN_BYTES, sizeInBytes);

        return databaseClient.querySingle(
                        StatementLabel.FIND_RECORDING, FIND_RECORDING_ID_BY_FILE_NAME_AND_SIZE, params,
                        (rs, _) -> rs.getString("id"))
                .flatMap(this::findRecording);
    }

    @Override
    public void insertRecordingFile(RecordingFile recordingFile) {
        //language=SQL
        String sql = """
                INSERT INTO recording_files (id, recording_id, filename, supported_type, uploaded_at, size_in_bytes)
                    VALUES (:id, :recording_id, :filename, :supported_type, :uploaded_at, :size_in_bytes)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", recordingFile.id())
                .addValue("recording_id", recordingFile.recordingId())
                .addValue("filename", recordingFile.filename())
                .addValue("supported_type", recordingFile.recordingFileType().name())
                .addValue("uploaded_at", recordingFile.uploadedAt().atOffset(ZoneOffset.UTC))
                .addValue("size_in_bytes", recordingFile.sizeInBytes());

        databaseClient.insert(StatementLabel.INSERT_RECORDING_FILE, sql, params);
    }

    @Override
    public void updateRecordingGroup(String recordingId, String groupId) {
        //language=sql
        String sql = "UPDATE recordings SET group_id = :group_id WHERE " + NO_PROJECT + " AND id = :recording_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("recording_id", recordingId)
                .addValue("group_id", groupId);

        databaseClient.update(StatementLabel.UPDATE_RECORDING_GROUP, sql, params);
    }

    // --- Group operations ---

    @Override
    public String insertGroup(String groupName) {
        //language=sql
        String sql = "INSERT INTO recording_groups (id, name, created_at) VALUES (:id, :name, :created_at)";

        String groupId = IDGenerator.generate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", groupId)
                .addValue("name", groupName)
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_GROUP, sql, params);
        return groupId;
    }

    @Override
    public boolean groupExists(String groupId) {
        //language=SQL
        String sql = "SELECT count(*) FROM recording_groups WHERE " + NO_PROJECT + " AND id = :group_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        return databaseClient.queryExists(StatementLabel.GROUP_EXISTS, sql, params);
    }

    @Override
    public Optional<RecordingGroup> findGroupById(String groupId) {
        //language=sql
        String sql = "SELECT * FROM recording_groups WHERE " + NO_PROJECT + " AND id = :group_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        return databaseClient.querySingle(
                StatementLabel.FIND_GROUP, sql, params, Mappers.projectRecordingGroupMapper());
    }

    @Override
    public List<RecordingGroup> findAllRecordingGroups() {
        //language=sql
        String sql = "SELECT * FROM recording_groups WHERE " + NO_PROJECT;

        return databaseClient.query(
                StatementLabel.FIND_ALL_GROUPS, sql, new MapSqlParameterSource(), Mappers.projectRecordingGroupMapper());
    }

    @Override
    public void deleteGroup(String groupId) {
        //language=sql
        String findRecordingsSql = "SELECT id FROM recordings WHERE " + NO_PROJECT + " AND group_id = :group_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        List<String> recordingIds = databaseClient.query(
                StatementLabel.FIND_RECORDINGS_IN_GROUP, findRecordingsSql, params,
                (rs, _) -> rs.getString("id"));

        //language=sql
        String deleteGroupSql = "DELETE FROM recording_groups WHERE id = :group_id";

        databaseClient.inTransaction(() -> {
            recordingIds.forEach(this::deleteRecordingWithFiles);
            databaseClient.delete(StatementLabel.DELETE_GROUP, deleteGroupSql, new MapSqlParameterSource().addValue("group_id", groupId));
        });
    }

    @Override
    public List<Recording> findRecordingsByGroupId(String groupId) {
        String recordingsSql = SELECT_RECORDINGS_WITH_PROFILE + NO_PROJECT + " AND r.group_id = :group_id";
        //language=sql
        String filesSql = "SELECT rf.* FROM recording_files rf JOIN recordings r ON rf.recording_id = r.id WHERE r."
                + NO_PROJECT + " AND r.group_id = :group_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("group_id", groupId);

        return findRecordingsWithFiles(
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDINGS, recordingsSql),
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDING_FILES, filesSql),
                params);
    }

    // --- Bundle operations ---

    //language=sql
    private static final String FIND_BUNDLES = "SELECT * FROM bundles WHERE " + NO_PROJECT;

    //language=sql
    private static final String BUNDLE_FILES_ORDER = " ORDER BY uploaded_at, filename";

    private static final String PARAM_BUNDLE_ID = "bundle_id";
    private static final String PARAM_GROUP_ID = "group_id";

    @Override
    public void insertBundle(Bundle bundle, List<Recording> members) {
        //language=SQL
        String sql = """
                INSERT INTO bundles (id, bundle_name, group_id, created_at)
                    VALUES (:id, :bundle_name, :group_id, :created_at)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", bundle.id())
                .addValue("bundle_name", bundle.bundleName())
                .addValue(PARAM_GROUP_ID, bundle.groupId())
                .addValue("created_at", bundle.createdAt().atOffset(ZoneOffset.UTC));

        databaseClient.inTransaction(() -> {
            databaseClient.insert(StatementLabel.INSERT_BUNDLE, sql, params);
            bundle.files().forEach(this::insertBundleFile);
            for (Recording member : members) {
                if (member.files().isEmpty()) {
                    throw new IllegalArgumentException("A bundle member needs at least one file: " + member.id());
                }
                insertRecording(member, member.files().getFirst());
                member.files().stream().skip(1).forEach(this::insertRecordingFile);
            }
        });
    }

    private void insertBundleFile(BundleFile file) {
        //language=SQL
        String sql = """
                INSERT INTO bundle_files (id, bundle_id, filename, supported_type, uploaded_at, size_in_bytes)
                    VALUES (:id, :bundle_id, :filename, :supported_type, :uploaded_at, :size_in_bytes)""";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", file.id())
                .addValue(PARAM_BUNDLE_ID, file.bundleId())
                .addValue("filename", file.filename())
                .addValue("supported_type", file.fileType().name())
                .addValue("uploaded_at", file.uploadedAt().atOffset(ZoneOffset.UTC))
                .addValue("size_in_bytes", file.sizeInBytes());

        databaseClient.insert(StatementLabel.INSERT_BUNDLE_FILE, sql, params);
    }

    @Override
    public List<Bundle> findAllBundles() {
        //language=sql
        String filesSql = "SELECT * FROM bundle_files WHERE " + NO_PROJECT + BUNDLE_FILES_ORDER;
        return withBundleFiles(
                databaseClient.query(StatementLabel.FIND_ALL_BUNDLES, FIND_BUNDLES,
                        new MapSqlParameterSource(), Mappers.bundleMapper()),
                new LabeledQuery(StatementLabel.FIND_ALL_BUNDLE_FILES, filesSql),
                new MapSqlParameterSource());
    }

    @Override
    public Optional<Bundle> findBundle(String bundleId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue(PARAM_BUNDLE_ID, bundleId);
        //language=sql
        String filesSql = "SELECT * FROM bundle_files WHERE " + NO_PROJECT
                + " AND bundle_id = :bundle_id" + BUNDLE_FILES_ORDER;
        return withBundleFiles(
                databaseClient.query(StatementLabel.FIND_BUNDLE, FIND_BUNDLES + " AND id = :bundle_id",
                        params, Mappers.bundleMapper()),
                new LabeledQuery(StatementLabel.FIND_BUNDLE_FILES, filesSql),
                params)
                .stream()
                .findFirst();
    }

    @Override
    public List<Bundle> findBundlesByGroupId(String groupId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue(PARAM_GROUP_ID, groupId);
        //language=sql
        String filesSql = "SELECT bf.* FROM bundle_files bf JOIN bundles b ON bf.bundle_id = b.id WHERE b."
                + NO_PROJECT + " AND b.group_id = :group_id ORDER BY bf.uploaded_at, bf.filename";
        return withBundleFiles(
                databaseClient.query(StatementLabel.FIND_BUNDLES_IN_GROUP, FIND_BUNDLES + " AND group_id = :group_id",
                        params, Mappers.bundleMapper()),
                new LabeledQuery(StatementLabel.FIND_ALL_BUNDLE_FILES, filesSql),
                params);
    }

    /**
     * Attaches the download-only files to their bundles, the way {@link #findRecordingsWithFiles} does
     * for recordings: one query for the files, skipped when there are no bundles.
     */
    private List<Bundle> withBundleFiles(
            List<Bundle> bundles, LabeledQuery filesQuery, MapSqlParameterSource params) {

        if (bundles.isEmpty()) {
            return bundles;
        }
        Map<String, List<BundleFile>> filesPerBundle = databaseClient.query(
                        filesQuery.label(), filesQuery.sql(), params, Mappers.bundleFileMapper())
                .stream()
                .collect(Collectors.groupingBy(BundleFile::bundleId));

        return bundles.stream()
                .map(bundle -> bundle.withFiles(filesPerBundle.getOrDefault(bundle.id(), List.of())))
                .toList();
    }

    @Override
    public List<Recording> findBundleMembers(String bundleId) {
        String recordingsSql = SELECT_RECORDINGS_WITH_PROFILE + NO_PROJECT + " AND r.bundle_id = :bundle_id";
        //language=sql
        String filesSql = "SELECT rf.* FROM recording_files rf JOIN recordings r ON rf.recording_id = r.id WHERE r."
                + NO_PROJECT + " AND r.bundle_id = :bundle_id ORDER BY rf.uploaded_at, rf.filename";

        return findRecordingsWithFiles(
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDINGS, recordingsSql),
                new LabeledQuery(StatementLabel.FIND_ALL_RECORDING_FILES, filesSql),
                new MapSqlParameterSource().addValue(PARAM_BUNDLE_ID, bundleId));
    }

    @Override
    public void deleteBundle(String bundleId) {
        // No 'project_id IS NULL' in a DELETE: it triggers the DuckDB ART index bug noted above.
        //language=sql
        String deleteFilesSql = "DELETE FROM bundle_files WHERE bundle_id = :bundle_id";
        //language=sql
        String deleteBundleSql = "DELETE FROM bundles WHERE id = :bundle_id";

        var params = new MapSqlParameterSource().addValue(PARAM_BUNDLE_ID, bundleId);

        databaseClient.inTransaction(() -> {
            databaseClient.delete(StatementLabel.DELETE_BUNDLE_FILES, deleteFilesSql, params);
            databaseClient.delete(StatementLabel.DELETE_BUNDLE, deleteBundleSql, params);
        });
    }

    @Override
    public void updateBundleGroup(String bundleId, String groupId) {
        //language=sql
        String sql = "UPDATE bundles SET group_id = :group_id WHERE " + NO_PROJECT + " AND id = :bundle_id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_BUNDLE_ID, bundleId)
                .addValue(PARAM_GROUP_ID, groupId);

        databaseClient.update(StatementLabel.UPDATE_BUNDLE_GROUP, sql, params);
    }
}
