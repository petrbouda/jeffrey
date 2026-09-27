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

package cafe.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.model.ProfilingStartEnd;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JdbcOverviewEventBuilder")
class JdbcOverviewEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Instant PROFILING_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PROFILING_END = Instant.parse("2026-01-01T00:10:00Z");
    private static final RelativeTimeRange TIME_RANGE = new RelativeTimeRange(
            new ProfilingStartEnd(PROFILING_START, PROFILING_END));

    private static final String GROUP = "orders";
    private static final String NAME = "findOrderById";
    private static final int SLOW_STATEMENT_LIMIT = 10;

    private static GenericRecord statement(String group, String name) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("group", group);
        fields.put("name", name);
        fields.put("sql", "SELECT * FROM orders WHERE id = ?");
        fields.put("rows", 1);
        Duration fromStart = Duration.ofSeconds(5);
        return new GenericRecord(
                Type.JDBC_QUERY,
                "JDBC Query",
                PROFILING_START.plus(fromStart),
                fromStart,
                Duration.ofMillis(40),
                null,
                null,
                1,
                0,
                fields);
    }

    @Nested
    @DisplayName("SlowStatements")
    class SlowStatements {

        @Test
        @DisplayName("keeps the statement's name and group in their own fields")
        void keepsTheNameAndTheGroupApart() {
            JdbcOverviewEventBuilder builder =
                    new JdbcOverviewEventBuilder(TIME_RANGE, SLOW_STATEMENT_LIMIT, null);

            builder.onRecord(statement(GROUP, NAME));
            JdbcOverviewData result = builder.build();

            JdbcSlowStatement slowest = result.slowStatements().getFirst();
            assertEquals(NAME, slowest.statementName());
            assertEquals(GROUP, slowest.statementGroup());
        }
    }
}
