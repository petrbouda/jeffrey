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

package pbouda.jeffrey.provider.profile.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JdbcProfileToolsRepository implements ProfileToolsRepository {

    //language=SQL
    private static final String COUNT_MATCHING_FRAMES = """
            SELECT COUNT(*) FROM frames WHERE class_name LIKE '%' || :pattern || '%'""";

    //language=SQL
    private static final String COUNT_AFFECTED_STACKTRACES = """
            SELECT COUNT(*) FROM stacktraces s
            WHERE EXISTS (
                SELECT 1 FROM unnest(s.frame_hashes) AS t(fh)
                JOIN frames f ON f.frame_hash = fh
                WHERE f.class_name LIKE '%' || :pattern || '%'
            )""";

    //language=SQL
    private static final String SAMPLE_MATCHING_FRAMES = """
            SELECT DISTINCT class_name, method_name
            FROM frames
            WHERE class_name LIKE '%' || :pattern || '%'
            LIMIT :limit""";

    //language=SQL
    private static final String FIND_MATCHING_FRAME_HASHES = """
            SELECT frame_hash FROM frames WHERE class_name LIKE '%' || :pattern || '%'""";

    //language=SQL
    private static final String FIND_AFFECTED_STACKTRACES = """
            SELECT stacktrace_hash, type_id, frame_hashes, tag_ids
            FROM stacktraces
            WHERE EXISTS (
                SELECT 1 FROM unnest(frame_hashes) AS t(fh)
                WHERE fh IN (:matching_hashes)
            )""";

    //language=SQL
    private static final String INSERT_SYNTHETIC_FRAME = """
            INSERT INTO frames (frame_hash, class_name, method_name, frame_type, line_number, bytecode_index)
            VALUES (:frame_hash, :class_name, '', 'Collapsed', 0, 0)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String UPDATE_EVENTS_STACKTRACE = """
            UPDATE events SET stacktrace_hash = :new_hash WHERE stacktrace_hash = :old_hash""";

    //language=SQL
    private static final String DELETE_STACKTRACES = """
            DELETE FROM stacktraces WHERE stacktrace_hash IN (:hashes)""";

    //language=SQL
    private static final String INSERT_STACKTRACE = """
            INSERT INTO stacktraces (stacktrace_hash, type_id, frame_hashes, tag_ids)
            VALUES (:hash, :type_id, :frame_hashes::BIGINT[], :tag_ids::INTEGER[])
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String DELETE_EVENTS_BY_STACKTRACES = """
            DELETE FROM events WHERE stacktrace_hash IN (:hashes)""";

    //language=SQL
    private static final String DELETE_ORPHANED_STACKTRACES = """
            DELETE FROM stacktraces
            WHERE stacktrace_hash NOT IN (
                SELECT DISTINCT stacktrace_hash FROM events WHERE stacktrace_hash IS NOT NULL
            )""";

    //language=SQL
    private static final String DELETE_ORPHANED_FRAMES = """
            DELETE FROM frames
            WHERE frame_hash NOT IN (
                SELECT DISTINCT unnest(frame_hashes) FROM stacktraces
            )""";

    //language=SQL
    private static final String DELETE_ORPHANED_THREADS = """
            DELETE FROM threads
            WHERE thread_hash NOT IN (
                SELECT DISTINCT thread_hash FROM events WHERE thread_hash IS NOT NULL
            )""";

    private final DatabaseClient databaseClient;

    public JdbcProfileToolsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILE_TOOLS);
    }

    @Override
    public int countMatchingFrames(String classNamePattern) {
        var params = new MapSqlParameterSource().addValue("pattern", classNamePattern);
        return (int) databaseClient.queryLong(StatementLabel.TOOLS_COUNT_MATCHING_FRAMES, COUNT_MATCHING_FRAMES, params);
    }

    @Override
    public int countAffectedStacktraces(String classNamePattern) {
        var params = new MapSqlParameterSource().addValue("pattern", classNamePattern);
        return (int) databaseClient.queryLong(StatementLabel.TOOLS_COUNT_AFFECTED_STACKTRACES, COUNT_AFFECTED_STACKTRACES, params);
    }

    @Override
    public List<FrameSample> sampleMatchingFrames(String classNamePattern, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("pattern", classNamePattern)
                .addValue("limit", limit);

        return databaseClient.query(StatementLabel.TOOLS_SAMPLE_MATCHING_FRAMES, SAMPLE_MATCHING_FRAMES, params,
                (rs, _) -> new FrameSample(rs.getString("class_name"), rs.getString("method_name")));
    }

    @Override
    public List<Long> findMatchingFrameHashes(String classNamePattern) {
        var params = new MapSqlParameterSource().addValue("pattern", classNamePattern);

        return databaseClient.query(StatementLabel.TOOLS_FIND_MATCHING_FRAME_HASHES, FIND_MATCHING_FRAME_HASHES, params,
                (rs, _) -> rs.getLong("frame_hash"));
    }

    @Override
    public List<StacktraceRecord> findAffectedStacktraces(List<Long> matchingFrameHashes) {
        var params = new MapSqlParameterSource().addValue("matching_hashes", matchingFrameHashes);

        return databaseClient.query(StatementLabel.TOOLS_FIND_AFFECTED_STACKTRACES, FIND_AFFECTED_STACKTRACES, params,
                (rs, _) -> new StacktraceRecord(
                        rs.getLong("stacktrace_hash"),
                        rs.getInt("type_id"),
                        toLongArray(rs, "frame_hashes"),
                        toIntArray(rs, "tag_ids")));
    }

    @Override
    public void insertSyntheticFrame(long frameHash, String className) {
        var params = new MapSqlParameterSource()
                .addValue("frame_hash", frameHash)
                .addValue("class_name", className);

        databaseClient.update(StatementLabel.TOOLS_INSERT_SYNTHETIC_FRAME, INSERT_SYNTHETIC_FRAME, params);
    }

    @Override
    public void applyStacktraceTransformation(Map<Long, Long> oldToNewHashMapping, List<StacktraceRecord> newStacktraces) {
        // Insert new stacktraces first
        for (StacktraceRecord st : newStacktraces) {
            var params = new MapSqlParameterSource()
                    .addValue("hash", st.stacktraceHash())
                    .addValue("type_id", st.typeId())
                    .addValue("frame_hashes", Arrays.toString(st.frameHashes()))
                    .addValue("tag_ids", Arrays.toString(st.tagIds()));

            databaseClient.update(StatementLabel.TOOLS_INSERT_STACKTRACE, INSERT_STACKTRACE, params);
        }

        // Update events to point to new stacktrace hashes
        for (var entry : oldToNewHashMapping.entrySet()) {
            var params = new MapSqlParameterSource()
                    .addValue("old_hash", entry.getKey())
                    .addValue("new_hash", entry.getValue());

            databaseClient.update(StatementLabel.TOOLS_UPDATE_EVENTS_STACKTRACE, UPDATE_EVENTS_STACKTRACE, params);
        }

        // Delete old stacktraces that are no longer referenced
        var deleteParams = new MapSqlParameterSource().addValue("hashes", oldToNewHashMapping.keySet());
        databaseClient.update(StatementLabel.TOOLS_DELETE_STACKTRACES, DELETE_STACKTRACES, deleteParams);
    }

    @Override
    public void deleteEventsByStacktraces(List<Long> stacktraceHashes) {
        var params = new MapSqlParameterSource().addValue("hashes", stacktraceHashes);
        databaseClient.update(StatementLabel.TOOLS_DELETE_EVENTS_BY_STACKTRACES, DELETE_EVENTS_BY_STACKTRACES, params);
    }

    @Override
    public long deleteOrphanedStacktraces() {
        return databaseClient.update(StatementLabel.TOOLS_DELETE_ORPHANED_STACKTRACES, DELETE_ORPHANED_STACKTRACES, new MapSqlParameterSource());
    }

    @Override
    public long deleteOrphanedFrames() {
        return databaseClient.update(StatementLabel.TOOLS_DELETE_ORPHANED_FRAMES, DELETE_ORPHANED_FRAMES, new MapSqlParameterSource());
    }

    @Override
    public long deleteOrphanedThreads() {
        return databaseClient.update(StatementLabel.TOOLS_DELETE_ORPHANED_THREADS, DELETE_ORPHANED_THREADS, new MapSqlParameterSource());
    }

    private static long[] toLongArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return new long[0];
        }
        Object[] objects = (Object[]) array.getArray();
        return Arrays.stream(objects)
                .mapToLong(o -> ((Number) o).longValue())
                .toArray();
    }

    private static int[] toIntArray(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return new int[0];
        }
        Object[] objects = (Object[]) array.getArray();
        return Arrays.stream(objects)
                .mapToInt(o -> ((Number) o).intValue())
                .toArray();
    }

}
