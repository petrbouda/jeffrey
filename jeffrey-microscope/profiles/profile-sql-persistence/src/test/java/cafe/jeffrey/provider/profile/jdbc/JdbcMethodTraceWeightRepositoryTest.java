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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcMethodTraceWeightRepositoryTest {

    private static final long MS = 1_000_000L;

    @Nested
    class DeriveSelfWeights {

        @Test
        void chargesACallOnlyForTimeItsCalleesDoNotAccountFor(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            Map<String, Long> weights = weightsByEntity(dataSource);
            assertEquals(5 * MS, weights.get("Probe#outer"), "10ms call containing a 3ms and a 2ms one");
            assertEquals(2 * MS, weights.get("Probe#inner"), "3ms call containing a 1ms one");
            assertEquals(1 * MS, weights.get("Probe#innermost"), "contains nothing");
            assertEquals(2 * MS, weights.get("Probe#sibling"), "contains nothing");
        }

        /**
         * The property the pass exists for. Before it, summing the four nested calls reports 16ms of
         * work inside a 10ms one.
         */
        @Test
        void selfTimesOfANestAddUpToTheOutermostCall(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            Map<String, Long> weights = weightsByEntity(dataSource);
            long nest = weights.get("Probe#outer")
                    + weights.get("Probe#inner")
                    + weights.get("Probe#innermost")
                    + weights.get("Probe#sibling");

            assertEquals(10 * MS, nest);
            assertEquals(16 * MS, durationsOfNest(dataSource), "the durations themselves still overlap");
        }

        @Test
        void leavesACallThatContainsNothingAlone(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            assertEquals(2 * MS, weightsByEntity(dataSource).get("Probe#alone"));
        }

        /**
         * Containment is per thread. Two threads running at the same wall-clock time are not a call
         * stack, and charging one for the other's time would be an outright fabrication.
         */
        @Test
        void doesNotNestCallsFromDifferentThreads(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            assertEquals(4 * MS, weightsByEntity(dataSource).get("Probe#other"));
        }

        /**
         * Two calls can hold the identical interval. Containment alone would let each claim the
         * other, so the row id breaks the tie in one direction only -- which is what keeps the two
         * from cancelling each other out, or from both surviving and reporting 2ms of work in 1ms.
         */
        @Test
        void nestsIdenticalIntervalsInOneDirection(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            assertEquals(1 * MS, sumOfWeights(dataSource, "Probe#twin"));
        }

        /**
         * A traced method that parks still spent that time. The park is not a traced call, so it
         * neither subtracts from its caller nor has its own weight rewritten.
         */
        @Test
        void ignoresEventsThatAreNotMethodTraces(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            assertEquals(500_000L, weightsByEntity(dataSource).get("Probe#park"));
        }

        @Test
        void countsTheCallsThatTurnedOutToContainAnother(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            assertEquals(3, repository.deriveSelfWeights(), "outer, inner and the first twin");
        }

        /**
         * Every weight is recomputed from the duration, which the pass never writes, so re-running it
         * -- a re-imported recording, a retried initialization -- must land where the first run did
         * rather than subtracting the children a second time.
         */
        @Test
        void isIdempotent(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();
            Map<String, Long> afterFirst = weightsByEntity(dataSource);
            repository.deriveSelfWeights();

            assertEquals(afterFirst, weightsByEntity(dataSource));
        }

        @Test
        void leavesDurationsUntouched(DataSource dataSource) throws SQLException {
            JdbcMethodTraceWeightRepository repository = repository(dataSource);

            repository.deriveSelfWeights();

            assertEquals(16 * MS, durationsOfNest(dataSource),
                    "the dashboard reads durations and wants each call's whole latency");
        }
    }

    @Nested
    class HasMethodTraces {

        @Test
        void findsThemWhenTheRecordingTracedMethods(DataSource dataSource) throws SQLException {
            assertTrue(repository(dataSource).hasMethodTraces());
        }

        @Test
        void reportsNoneOnARecordingWithoutThem(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcMethodTraceWeightRepository repository =
                    new JdbcMethodTraceWeightRepository(new DatabaseClientProvider(dataSource));

            assertFalse(repository.hasMethodTraces());
        }
    }

    private static JdbcMethodTraceWeightRepository repository(DataSource dataSource) throws SQLException {
        TestUtils.executeSql(dataSource, "sql/events/insert-method-trace-nesting.sql");
        return new JdbcMethodTraceWeightRepository(new DatabaseClientProvider(dataSource));
    }

    private static Map<String, Long> weightsByEntity(DataSource dataSource) throws SQLException {
        Map<String, Long> weights = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT weight_entity, SUM(weight) AS weight FROM events_raw GROUP BY weight_entity");
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                weights.put(rs.getString("weight_entity"), rs.getLong("weight"));
            }
        }
        return weights;
    }

    private static long sumOfWeights(DataSource dataSource, String entity) throws SQLException {
        return weightsByEntity(dataSource).get(entity);
    }

    private static long durationsOfNest(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT SUM(duration) FROM events_raw WHERE weight_entity IN "
                             + "('Probe#outer', 'Probe#inner', 'Probe#innermost', 'Probe#sibling')");
             ResultSet rs = statement.executeQuery()) {

            rs.next();
            return rs.getLong(1);
        }
    }
}
