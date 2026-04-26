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

package pbouda.jeffrey.provider.profile.sql.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.profile.repository.ProfileToolsRepository.FrameSample;
import pbouda.jeffrey.provider.profile.repository.ProfileToolsRepository.StacktraceRecord;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileToolsRepositoryTest {

    private static final String TEST_DATA = "sql/tools/insert-frames-stacktraces-events.sql";

    private static JdbcProfileToolsRepository createRepository(DataSource dataSource) {
        return new JdbcProfileToolsRepository(new DatabaseClientProvider(dataSource));
    }

    @Nested
    class CountMatchingFramesMethod {

        @Test
        void countsFramesMatchingPattern(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(2, repository.countMatchingFrames("UserService"));
        }

        @Test
        void countsFramesMatchingBroaderPattern(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // createOrder (103) + cancelOrder (106)
            assertEquals(2, repository.countMatchingFrames("OrderService"));
        }

        @Test
        void returnsZeroWhenNoMatch(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(0, repository.countMatchingFrames("NonExistentClass"));
        }

        @Test
        void returnsZeroWhenNoData(DataSource dataSource) {
            var repository = createRepository(dataSource);

            assertEquals(0, repository.countMatchingFrames("UserService"));
        }
    }

    @Nested
    class CountAffectedStacktracesMethod {

        @Test
        void countsStacktracesContainingMatchingFrames(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // UserService frames (101, 102) appear in ST 2001 and ST 2002
            assertEquals(2, repository.countAffectedStacktraces("UserService"));
        }

        @Test
        void countsStacktracesForWidelyUsedFrame(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // ConnectionPool frame (104) appears in ST 2001, 2002, 2004
            assertEquals(3, repository.countAffectedStacktraces("ConnectionPool"));
        }

        @Test
        void countsAllStacktracesForFrameInAll(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // Thread.run frame (105) appears in all 4 stacktraces
            assertEquals(4, repository.countAffectedStacktraces("java.lang.Thread"));
        }

        @Test
        void returnsZeroWhenNoMatch(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(0, repository.countAffectedStacktraces("NonExistentClass"));
        }
    }

    @Nested
    class SampleMatchingFramesMethod {

        @Test
        void returnsSamplesMatchingPattern(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<FrameSample> samples = repository.sampleMatchingFrames("UserService", 10);

            assertEquals(2, samples.size());
            Set<String> methods = samples.stream().map(FrameSample::methodName).collect(Collectors.toSet());
            assertEquals(Set.of("getUser", "saveUser"), methods);
        }

        @Test
        void respectsLimit(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<FrameSample> samples = repository.sampleMatchingFrames("com.example", 2);

            assertEquals(2, samples.size());
        }

        @Test
        void returnsEmptyWhenNoMatch(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<FrameSample> samples = repository.sampleMatchingFrames("NonExistent", 10);

            assertTrue(samples.isEmpty());
        }
    }

    @Nested
    class FindMatchingFrameHashesMethod {

        @Test
        void returnsHashesForMatchingFrames(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<Long> hashes = repository.findMatchingFrameHashes("UserService");

            assertEquals(2, hashes.size());
            assertTrue(hashes.containsAll(List.of(101L, 102L)));
        }

        @Test
        void returnsEmptyWhenNoMatch(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<Long> hashes = repository.findMatchingFrameHashes("NonExistent");

            assertTrue(hashes.isEmpty());
        }
    }

    @Nested
    class FindAffectedStacktracesMethod {

        @Test
        void returnsStacktracesContainingAnyMatchingHash(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // UserService frame hashes
            List<StacktraceRecord> result = repository.findAffectedStacktraces(List.of(101L, 102L));

            assertEquals(2, result.size());
            Set<Long> hashes = result.stream().map(StacktraceRecord::stacktraceHash).collect(Collectors.toSet());
            assertEquals(Set.of(2001L, 2002L), hashes);
        }

        @Test
        void returnsStacktraceWithCorrectFields(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // Only frame 103 -> ST 2003
            List<StacktraceRecord> result = repository.findAffectedStacktraces(List.of(103L));

            assertEquals(1, result.size());
            StacktraceRecord record = result.getFirst();
            assertEquals(2003L, record.stacktraceHash());
            assertEquals(1, record.typeId());
            assertArrayEquals(new long[]{103, 105}, record.frameHashes());
            assertArrayEquals(new int[]{2}, record.tagIds());
        }

        @Test
        void returnsEmptyWhenNoMatchingHashes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            List<StacktraceRecord> result = repository.findAffectedStacktraces(List.of(999L));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class InsertSyntheticFrameMethod {

        @Test
        void insertsSyntheticFrame(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            repository.insertSyntheticFrame(999L, "[collapsed] com.example.app.UserService");

            List<FrameSample> samples = repository.sampleMatchingFrames("[collapsed] com.example.app.UserService", 10);
            assertEquals(1, samples.size());
            assertEquals("[collapsed] com.example.app.UserService", samples.getFirst().className());
        }

        @Test
        void doesNotFailOnDuplicateInsert(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            repository.insertSyntheticFrame(999L, "[collapsed]");
            repository.insertSyntheticFrame(999L, "[collapsed]");

            // No exception — ON CONFLICT DO NOTHING
            assertEquals(1, repository.countMatchingFrames("[collapsed]"));
        }
    }

    @Nested
    class ApplyStacktraceTransformationMethod {

        @Test
        void transformsStacktracesAndUpdatesEvents(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // Replace ST 2001 with a new stacktrace (9001) that collapses UserService frames
            var newStacktrace = new StacktraceRecord(9001L, 1, new long[]{999, 105}, new int[]{1});
            repository.insertSyntheticFrame(999L, "[collapsed] com.example.app.UserService");
            repository.applyStacktraceTransformation(Map.of(2001L, 9001L), List.of(newStacktrace));

            // Old stacktrace 2001 should be deleted, events should point to 9001
            // Verify the new stacktrace exists by finding it via the synthetic frame
            List<StacktraceRecord> affected = repository.findAffectedStacktraces(List.of(999L));
            assertEquals(1, affected.size());
            assertEquals(9001L, affected.getFirst().stacktraceHash());
        }
    }

    @Nested
    class DeleteEventsByStacktracesMethod {

        @Test
        void deletesEventsReferencingStacktraces(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // ST 2003 has 2 events
            repository.deleteEventsByStacktraces(List.of(2003L));

            // After deleting, ST 2003 should become orphaned
            long orphaned = repository.deleteOrphanedStacktraces();
            assertEquals(1, orphaned);
        }

        @Test
        void doesNothingWhenNoMatchingStacktraces(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            repository.deleteEventsByStacktraces(List.of(9999L));

            // No orphans created
            assertEquals(0, repository.deleteOrphanedStacktraces());
        }
    }

    @Nested
    class DeleteOrphanedStacktracesMethod {

        @Test
        void deletesStacktracesWithNoEvents(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // First remove all events for ST 2004
            repository.deleteEventsByStacktraces(List.of(2004L));

            long deleted = repository.deleteOrphanedStacktraces();

            assertEquals(1, deleted);
        }

        @Test
        void returnsZeroWhenNoOrphans(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(0, repository.deleteOrphanedStacktraces());
        }
    }

    @Nested
    class DeleteOrphanedFramesMethod {

        @Test
        void deletesFramesNotReferencedByStacktraces(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // Remove events for ST 2004 (cancelOrder) then cleanup orphans
            repository.deleteEventsByStacktraces(List.of(2004L));
            repository.deleteOrphanedStacktraces();

            // Frame 106 (cancelOrder) was only in ST 2004 -> now orphaned
            long deleted = repository.deleteOrphanedFrames();

            assertEquals(1, deleted);
        }

        @Test
        void returnsZeroWhenNoOrphans(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(0, repository.deleteOrphanedFrames());
        }
    }

    @Nested
    class DeleteOrphanedThreadsMethod {

        @Test
        void deletesThreadsNotReferencedByEvents(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            // Remove events for threads worker-1 (1002) and worker-2 (1003)
            // ST 2002 -> thread 1002, ST 2003 -> thread 1002+1003, ST 2004 -> thread 1003
            repository.deleteEventsByStacktraces(List.of(2002L, 2003L, 2004L));

            long deleted = repository.deleteOrphanedThreads();

            // Threads 1002 and 1003 are no longer referenced
            assertEquals(2, deleted);
        }

        @Test
        void returnsZeroWhenNoOrphans(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, TEST_DATA);
            var repository = createRepository(dataSource);

            assertEquals(0, repository.deleteOrphanedThreads());
        }
    }

}
