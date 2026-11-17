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

package cafe.jeffrey.provider.profile.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.provider.profile.api.ProfileExceptionRepository;
import cafe.jeffrey.provider.profile.api.ThrowCountRecord;
import cafe.jeffrey.provider.profile.api.ThrowMessageRecord;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcProfileExceptionRepository implements ProfileExceptionRepository {

    /**
     * What a throw whose event carried no {@code thrownClass} is grouped under, so its count is not
     * lost from the totals.
     */
    private static final String UNKNOWN_CLASS = "<unknown>";

    private static final String UNKNOWN_CLASS_PARAM = "unknown_class";

    /** A recorded line of 0 means the recording carries none; the readers expect null for that. */
    private static final int NO_LINE = 0;

    //language=SQL
    private static final String THROW_COUNTS = """
            WITH <<distinct_throws>>
            SELECT
                COALESCE(thrown_class, :unknown_class)       AS thrown_class,
                event_type = :error_throw_event_type         AS error,
                stacktrace_hash,
                thread_hash,
                COUNT(*)                                     AS throw_count
            FROM distinct_throws
            GROUP BY ALL
            """.replace("<<distinct_throws>>", ThrowEvents.DISTINCT_THROWS_CTES);

    // A message that is only whitespace says nothing; it is left out rather than shown as a blank row.
    //language=SQL
    private static final String THROW_MESSAGES = """
            WITH <<distinct_throws>>
            SELECT
                COALESCE(thrown_class, :unknown_class)       AS thrown_class,
                stacktrace_hash,
                message,
                COUNT(*)                                     AS message_count
            FROM distinct_throws
            WHERE message IS NOT NULL
              AND trim(message) <> ''
            GROUP BY ALL
            """.replace("<<distinct_throws>>", ThrowEvents.DISTINCT_THROWS_CTES);

    // frame_hashes is stored root-first, so the frame on top of the stack is the highest subscript.
    //language=SQL
    private static final String THROW_STACKTRACES = """
            WITH throw_stacks AS (
                SELECT DISTINCT stacktrace_hash
                FROM events_raw
                WHERE event_type IN (:exception_event_types)
                  AND stacktrace_hash IS NOT NULL
            ),
            positioned AS (
                SELECT
                    s.stacktrace_hash,
                    unnest(s.frame_hashes)                AS frame_hash,
                    generate_subscripts(s.frame_hashes, 1) AS depth
                FROM stacktraces s
                JOIN throw_stacks ts ON ts.stacktrace_hash = s.stacktrace_hash
            )
            SELECT p.stacktrace_hash, f.class_name, f.method_name, f.line_number
            FROM positioned p
            JOIN frames f ON f.frame_hash = p.frame_hash
            ORDER BY p.stacktrace_hash, p.depth DESC
            """;

    private final DatabaseClient databaseClient;

    public JdbcProfileExceptionRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILES);
    }

    @Override
    public List<ThrowCountRecord> throwCounts() {
        return databaseClient.query(
                StatementLabel.THROW_COUNTS,
                THROW_COUNTS,
                params(),
                (rs, _) -> new ThrowCountRecord(
                        rs.getString("thrown_class"),
                        rs.getBoolean("error"),
                        JdbcNulls.longOrNull(rs, "stacktrace_hash"),
                        JdbcNulls.longOrNull(rs, "thread_hash"),
                        rs.getLong("throw_count")));
    }

    @Override
    public List<ThrowMessageRecord> throwMessages() {
        return databaseClient.query(
                StatementLabel.THROW_MESSAGES,
                THROW_MESSAGES,
                params(),
                (rs, _) -> new ThrowMessageRecord(
                        rs.getString("thrown_class"),
                        JdbcNulls.longOrNull(rs, "stacktrace_hash"),
                        rs.getString("message"),
                        rs.getLong("message_count")));
    }

    @Override
    public Map<Long, List<ThrowFrame>> throwStacktraces() {
        Map<Long, List<ThrowFrame>> stacks = new HashMap<>();
        databaseClient.queryStream(
                StatementLabel.THROW_STACKTRACES,
                THROW_STACKTRACES,
                ThrowEvents.params(),
                (rs, _) -> new StackRow(
                        rs.getLong("stacktrace_hash"),
                        new ThrowFrame(
                                rs.getString("class_name"),
                                rs.getString("method_name"),
                                lineOrNull(rs.getInt("line_number")))),
                row -> stacks.computeIfAbsent(row.stacktraceHash(), _ -> new ArrayList<>()).add(row.frame()));
        return stacks;
    }

    private static MapSqlParameterSource params() {
        return ThrowEvents.params().addValue(UNKNOWN_CLASS_PARAM, UNKNOWN_CLASS);
    }

    private static Integer lineOrNull(int line) {
        return line == NO_LINE ? null : line;
    }

    private record StackRow(long stacktraceHash, ThrowFrame frame) {
    }
}
