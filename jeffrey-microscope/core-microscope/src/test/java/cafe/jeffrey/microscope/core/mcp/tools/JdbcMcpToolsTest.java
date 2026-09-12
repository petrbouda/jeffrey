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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileCustomManager;
import cafe.jeffrey.profile.manager.ProfileFeaturesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.PoolConfiguration;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.PoolStatistics;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcGroup;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcHeader;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOperationStats;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.timeseries.SingleSerie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JdbcMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String GROUP = "orders";
    private static final String UNKNOWN_GROUP = "no-such-group";
    private static final String STATEMENT_NAME = "findOrdersByCustomer";
    private static final String POOL_NAME = "HikariPool-1";
    private static final String STATEMENTS_VIEW_LINK = "/profiles/p-1/technologies/jdbc";
    private static final String STATEMENT_GROUPS_VIEW_LINK =
            "/profiles/p-1/technologies/jdbc/statement-groups";
    private static final String POOL_VIEW_LINK = "/profiles/p-1/technologies/jdbc-pool";
    private static final String SHORT_SQL = "SELECT * FROM orders WHERE customer_id = ?";
    private static final String TRUNCATION_SUFFIX = "...";

    /** Longer than the tool's own SQL cap, so a generated query can be seen being shortened. */
    private static final int LONGER_THAN_THE_SQL_CAP = 700;
    private static final String SQL_TAIL_MARKER = "TAIL_MARKER";

    @Mock
    ProfileManager profileManager;

    @Mock
    ProfileCustomManager customManager;

    @Mock
    ProfileFeaturesManager featuresManager;

    @Mock
    JdbcStatementManager statementManager;

    @Mock
    JdbcPoolManager poolManager;

    /**
     * Every answer carries a link into the UI, and {@code UiLinks} reads the request bound to the
     * current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.custom()).thenReturn(customManager);
        when(profileManager.featuresManager()).thenReturn(featuresManager);
        when(customManager.jdbcStatementManager()).thenReturn(statementManager);
        when(customManager.jdbcPoolManager()).thenReturn(poolManager);
        when(featuresManager.getDisabledFeatures()).thenReturn(List.of());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private JdbcMcpTools tools() {
        return new JdbcMcpTools(profileManager);
    }

    private static JdbcOverviewData overview(long errorCount, String sql) {
        return new JdbcOverviewData(
                new JdbcHeader(9_400, 820_000_000L, 410_000_000L, 120_000_000L,
                        new BigDecimal("99.8"), errorCount),
                List.of(new JdbcOperationStats("SELECT", 8_900), new JdbcOperationStats("INSERT", 500)),
                List.of(new JdbcGroup(GROUP, 4_100, 12_000, 44_000_000_000L, 820_000_000L,
                        410_000_000L, 120_000_000L, errorCount, List.of())),
                List.of(statement(sql)),
                new SingleSerie("executionTime", List.of()),
                new SingleSerie("statementCount", List.of()));
    }

    private static JdbcOverviewData empty() {
        return new JdbcOverviewData(
                new JdbcHeader(0, 0, 0, 0, BigDecimal.ZERO, 0),
                List.of(),
                List.of(),
                List.of(),
                new SingleSerie("executionTime", List.of()),
                new SingleSerie("statementCount", List.of()));
    }

    private static JdbcSlowStatement statement(String sql) {
        return new JdbcSlowStatement(
                1_000L, sql, STATEMENT_NAME, GROUP, "SELECT",
                820_000_000L, 42, "customerId=17", true, false, false);
    }

    private static String oversizedSql() {
        return "SELECT * FROM orders WHERE id IN ("
                + "0,".repeat(LONGER_THAN_THE_SQL_CAP) + ") /* " + SQL_TAIL_MARKER + " */";
    }

    private static JdbcPoolData pool(int maxPendingThreadCount, long timeoutsCount) {
        return new JdbcPoolData(
                POOL_NAME,
                new PoolConfiguration(20, 5),
                new PoolStatistics(18, 16, new BigDecimal("11.4"), maxPendingThreadCount,
                        new BigDecimal("3.2"), timeoutsCount, new BigDecimal("0.1")),
                List.of());
    }

    @Nested
    class Overview {

        @Test
        void carriesTheHeaderTheOperationMixAndTheGroups() {
            when(statementManager.overviewData()).thenReturn(overview(0, SHORT_SQL));

            String out = tools().overview();

            assertTrue(out.contains("\"statementCount\":9400"), out);
            assertTrue(out.contains("\"label\":\"SELECT\""), out);
            assertTrue(out.contains("\"group\":\"" + GROUP + "\""), out);
            assertTrue(out.contains(STATEMENTS_VIEW_LINK), out);
        }

        /**
         * An absent event type produces a well-formed zero dashboard, which reads as "the database is
         * fine" rather than "nothing was measured".
         */
        @Test
        void reportsMissingDataAsAProfilerFindingRatherThanAnEmptyDashboard() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.JDBC_STATEMENTS_DASHBOARD));

            String out = tools().overview();

            assertTrue(out.contains("holds no JDBC statement data"), out);
            assertFalse(out.contains("statementCount"), out);
        }

        /**
         * The SQL is here to identify a statement rather than to be run, and one generated query with
         * a large IN-list would otherwise crowd the rest of the answer out of the reply.
         */
        @Test
        void shortensTheSqlOfAStatementThatCarriesAGeneratedInList() {
            when(statementManager.overviewData()).thenReturn(overview(0, oversizedSql()));

            String out = tools().overview();

            assertTrue(out.contains(TRUNCATION_SUFFIX), out);
            assertFalse(out.contains(SQL_TAIL_MARKER), out);
        }

        @Test
        void leavesTheChartSeriesOut() {
            when(statementManager.overviewData()).thenReturn(overview(0, SHORT_SQL));

            String out = tools().overview();

            assertFalse(out.contains("executionTimeSerie"), out);
            assertFalse(out.contains("statementCountSerie"), out);
        }

        /**
         * Routing is gated on the failure having happened, never on a judgement about how many.
         */
        @Test
        void namesTheFailureTrailOnlyWhenAStatementActuallyFailed() {
            when(statementManager.overviewData()).thenReturn(overview(0, SHORT_SQL));
            assertFalse(tools().overview().contains("traces_notifications"));

            when(statementManager.overviewData()).thenReturn(overview(7, SHORT_SQL));
            assertTrue(tools().overview().contains("traces_notifications"));
        }
    }

    @Nested
    class StatementGroup {

        @Test
        void narrowsToTheRequestedGroupAndLinksItsPage() {
            when(statementManager.overviewData(GROUP)).thenReturn(overview(0, SHORT_SQL));

            String out = tools().statementGroup(GROUP);

            assertTrue(out.contains("\"group\":\"" + GROUP + "\""), out);
            assertTrue(out.contains(STATEMENT_GROUPS_VIEW_LINK), out);
            assertTrue(out.contains("group=" + GROUP), out);
        }

        /**
         * The manager reads a null group as "no filter", so an omitted one would hand back the whole
         * statement dashboard under the heading of one group.
         */
        @Test
        void refusesAMissingGroupRatherThanReturningEveryStatement() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> tools().statementGroup(null));

            assertTrue(thrown.getMessage().contains("group is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("jdbc_overview"), thrown.getMessage());
        }

        @Test
        void refusesABlankGroupTheSameWay() {
            assertThrows(IllegalArgumentException.class, () -> tools().statementGroup("   "));
        }

        @Test
        void reportsAnUnknownGroupAsAToolError() {
            when(statementManager.overviewData(UNKNOWN_GROUP)).thenReturn(empty());

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> tools().statementGroup(UNKNOWN_GROUP));

            assertTrue(error.getMessage().contains("No statements were recorded for group '" + UNKNOWN_GROUP + "'"),
                    error.getMessage());
        }
    }

    @Nested
    class Pools {

        @Test
        void carriesTheConfiguredSizesAgainstWhatWasActuallyUsed() {
            when(poolManager.allPoolsData()).thenReturn(List.of(pool(0, 0)));

            String out = tools().pools();

            assertTrue(out.contains("\"poolName\":\"" + POOL_NAME + "\""), out);
            assertTrue(out.contains("\"maxConnectionCount\":20"), out);
            assertTrue(out.contains("\"peakActiveConnectionCount\":16"), out);
            assertTrue(out.contains(POOL_VIEW_LINK), out);
        }

        @Test
        void reportsMissingPoolDataSeparatelyFromMissingStatementData() {
            when(featuresManager.getDisabledFeatures())
                    .thenReturn(List.of(FeatureType.JDBC_POOL_DASHBOARD));

            String out = tools().pools();

            assertTrue(out.contains("holds no JDBC connection-pool data"), out);
            assertTrue(out.contains("jdbc_overview"), out);
        }

        /**
         * A thread waiting for a connection at all is what makes the pool worth explaining - the tool
         * routes on the event having happened rather than on how often, which would be a verdict.
         */
        @Test
        void explainsPoolContentionOnlyWhenAThreadActuallyWaitedOrWasRefused() {
            when(poolManager.allPoolsData()).thenReturn(List.of(pool(0, 0)));
            assertFalse(tools().pools().contains("traces_operations"));

            when(poolManager.allPoolsData()).thenReturn(List.of(pool(3, 0)));
            assertTrue(tools().pools().contains("traces_operations"));

            when(poolManager.allPoolsData()).thenReturn(List.of(pool(0, 2)));
            assertTrue(tools().pools().contains("traces_operations"));
        }
    }
}
