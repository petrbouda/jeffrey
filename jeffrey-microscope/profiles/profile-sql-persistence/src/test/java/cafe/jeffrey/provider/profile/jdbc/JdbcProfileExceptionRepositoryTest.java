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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.provider.profile.api.ThrowCountRecord;
import cafe.jeffrey.provider.profile.api.ThrowMessageRecord;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileExceptionRepositoryTest {

    private static final String TEST_DATA = "sql/events/insert-exception-throws.sql";

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String STACK_OVERFLOW = "java.lang.StackOverflowError";
    private static final String UNKNOWN = "<unknown>";

    private static JdbcProfileExceptionRepository repository(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, TEST_DATA);
        return new JdbcProfileExceptionRepository(new DatabaseClientProvider(dataSource));
    }

    private static Map<String, Long> countsByClass(List<ThrowCountRecord> counts) {
        return counts.stream().collect(Collectors.groupingBy(
                ThrowCountRecord::thrownClass, Collectors.summingLong(ThrowCountRecord::count)));
    }

    @Nested
    class ThrowCounts {

        @Test
        void countsEachErrorOnceNotThreeTimes(DataSource dataSource) throws SQLException {
            List<ThrowCountRecord> counts = repository(dataSource).throwCounts();

            assertEquals(1L, countsByClass(counts).get(STACK_OVERFLOW));
            assertTrue(counts.stream()
                    .filter(count -> STACK_OVERFLOW.equals(count.thrownClass()))
                    .allMatch(ThrowCountRecord::error));
        }

        @Test
        void splitsByThreadSoDistinctThreadsCanBeCounted(DataSource dataSource) throws SQLException {
            List<ThrowCountRecord> io = repository(dataSource).throwCounts().stream()
                    .filter(count -> IO_EXCEPTION.equals(count.thrownClass()))
                    .toList();

            assertEquals(3L, io.stream().mapToLong(ThrowCountRecord::count).sum());
            assertEquals(2, io.size());
            assertTrue(io.stream().noneMatch(ThrowCountRecord::error));
            assertTrue(io.stream().allMatch(count -> Long.valueOf(4001).equals(count.stacktraceHash())));
        }

        @Test
        void keepsAThrowWithNoClassUnderUnknown(DataSource dataSource) throws SQLException {
            Map<String, Long> byClass = countsByClass(repository(dataSource).throwCounts());

            assertEquals(1L, byClass.get(UNKNOWN));
            assertEquals(3, byClass.size());
        }
    }

    @Nested
    class ThrowMessages {

        @Test
        void countsMessagesIncludingPooledOnesAndDropsBlank(DataSource dataSource) throws SQLException {
            List<ThrowMessageRecord> messages = repository(dataSource).throwMessages();

            assertEquals(List.of(new ThrowMessageRecord(IO_EXCEPTION, 4001L, "connection reset", 2)), messages);
        }
    }

    @Nested
    class ThrowStacktraces {

        @Test
        void returnsEveryThrowStackTopFirst(DataSource dataSource) throws SQLException {
            Map<Long, List<ThrowFrame>> stacks = repository(dataSource).throwStacktraces();

            assertEquals(2, stacks.size());
            assertEquals(List.of(
                    new ThrowFrame("java.lang.Throwable", "<init>", 266),
                    new ThrowFrame("java.lang.Exception", "<init>", 113),
                    new ThrowFrame(IO_EXCEPTION, "<init>", 62),
                    new ThrowFrame("com.acme.orders.OrderService", "place", 42),
                    new ThrowFrame("java.lang.Thread", "run", 1583)), stacks.get(4001L));
        }

        @Test
        void readsAZeroLineAsUnknown(DataSource dataSource) throws SQLException {
            List<ThrowFrame> stack = repository(dataSource).throwStacktraces().get(4002L);

            assertEquals(new ThrowFrame("com.acme.orders.Recursive", "descend", null), stack.get(2));
        }
    }
}
