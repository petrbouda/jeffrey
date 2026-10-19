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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The parses are DuckDB's own, read through {@code json_serialize_sql}, so these pin the shape of the
 * engine's tree as much as the rule: an upgrade that changes either fails here rather than in a
 * caller's session.
 */
@DuckDBTest
class JsonArrowPrecedenceTest {

    @Nested
    @DisplayName("An arrow that swallowed its neighbours")
    class Swallowed {

        @ParameterizedTest
        @ValueSource(strings = {
                "SELECT fields FROM events WHERE event_type = 'jeffrey.TraceSpan' AND fields->>'traceId' = 'a07'",
                "SELECT fields FROM events WHERE fields->>'id' = '1' OR fields->>'parentId' = '0'",
                "SELECT fields FROM events WHERE duration > 0 AND fields->'traceId' = 'a07'",
                "SELECT fields->>'endTime' - fields->>'startTime' FROM events",
                "SELECT fields->>'size'::BIGINT FROM events",
                "SELECT fields FROM events WHERE fields->'parentId' = 0",
                "SELECT * FROM events WHERE event_type = ? AND (duration > 0 AND fields->>'a' = 'b')"
        })
        @DisplayName("is recognised")
        void isRecognised(String sql, DataSource dataSource) {
            assertEquals(Optional.of(JsonArrowPrecedence.HINT), JsonArrowPrecedence.hintFor(dataSource, sql));
        }
    }

    @Nested
    @DisplayName("An arrow that reads as written")
    class AsWritten {

        @ParameterizedTest
        @ValueSource(strings = {
                "SELECT fields FROM events WHERE event_type = 'jeffrey.TraceSpan' AND (fields->>'traceId') = 'a07'",
                "SELECT fields FROM events WHERE fields->>'traceId' = 'a07' AND event_type = 'jeffrey.TraceSpan'",
                "SELECT (fields->>'size')::BIGINT, CAST(fields->>'size' AS BIGINT) FROM events",
                "SELECT fields->'outer'->>'inner', json_extract(fields, '$.a')->>'b' FROM events",
                "SELECT SUM(CAST(fields->>'bytes' AS BIGINT)) / 1000000.0 FROM events",
                "SELECT list_filter([1, 2, 3], x -> x > 1), list_transform([1, 2], (x, i) -> x + i)",
                "SELECT nope FROM events",
                "not a query at all"
        })
        @DisplayName("is left alone")
        void isLeftAlone(String sql, DataSource dataSource) {
            assertEquals(Optional.empty(), JsonArrowPrecedence.hintFor(dataSource, sql));
        }
    }
}
