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

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcGroup;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcHeader;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOperationStats;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * What the profiled JVM asked its database, and what waiting for the answers cost.
 * <p>
 * Two dashboards that are usually read together but fail independently: the statements themselves,
 * and the connection pool in front of them. A slow request whose statements are all fast is usually
 * waiting for a connection, which is what {@code jdbc_pools} shows and the statement view cannot.
 * <p>
 * SQL text is truncated. It is here to identify a statement, not to be executed, and a single
 * generated query with a large IN-list would otherwise crowd out the rest of the answer.
 */
public class JdbcMcpTools {

    private static final String STATEMENTS_VIEW = "technologies/jdbc";
    private static final String STATEMENT_GROUPS_VIEW = "technologies/jdbc/statement-groups";
    private static final String POOL_VIEW = "technologies/jdbc-pool";
    private static final String GROUP_PARAM = "group";

    private static final int MAX_GROUPS = 40;
    private static final int MAX_SQL_CHARS = 500;
    private static final int MAX_PARAMETERS_CHARS = 200;
    private static final String TRUNCATION_SUFFIX = "...";

    private static final String NO_STATEMENT_DATA =
            "This profile holds no JDBC statement data: the recording did not capture the JDBC query "
                    + "events. That is a profiler-configuration finding worth reporting rather than "
                    + "evidence that the application does not use a database.";

    private static final String NO_POOL_DATA =
            "This profile holds no JDBC connection-pool data: the recording did not capture the pooled "
                    + "connection events. Statement timings may still be available through jdbc_overview.";

    private static final String STEP_GROUP =
            "For one statement group on its own - its percentiles and its slowest statements - "
                    + "jdbc_statementGroup takes a name from the groups list above.";
    private static final String STEP_POOLS =
            "Statements that are all fast while requests are not means the time went waiting for a "
                    + "connection rather than running SQL: jdbc_pools.";
    private static final String STEP_STATEMENTS =
            "The statements these connections carried are in jdbc_overview.";
    private static final String STEP_STATEMENT_ERRORS =
            "Some statements failed. What the application reported about them is in "
                    + "traces_notifications, when this profile carries traces.";
    private static final String STEP_POOL_WAITS =
            "This pool made threads wait for a connection, or timed out handing one over. "
                    + "traces_notifications carries what the application said about it, and the "
                    + "requests that paid for it are in traces_operations.";

    private static final String NO_SUCH_GROUP =
            "No statements were recorded for group '%s'. Call jdbc_overview and take a name from its "
                    + "groups list.";

    private static final String NO_GROUP_RECOVERY =
            "Call jdbc_overview and take a name from its groups list.";

    private final ProfileManager profileManager;

    public JdbcMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The JDBC statement dashboard: statement count, execution-time percentiles, "
            + "success rate and error count, the operation mix (SELECT/INSERT/UPDATE/...), the "
            + "statement groups ranked by cost, and the slowest individual statements with their SQL. "
            + "Start here for 'is the database the bottleneck'.")
    public String overview() {
        if (DashboardFeature.missing(profileManager, FeatureType.JDBC_STATEMENTS_DASHBOARD)) {
            return NO_STATEMENT_DATA;
        }

        JdbcOverviewData data = profileManager.custom().jdbcStatementManager().overviewData();
        return LinkedOutput.json(dashboard(data, UiLinks.view(profileId(), STATEMENTS_VIEW)));
    }

    @Tool(description = "One statement group in detail: the same percentiles and slowest statements as "
            + "the overview, narrowed to a single group. Use it after jdbc_overview has named the "
            + "group worth looking at.")
    public String statementGroup(
            @ToolParam(required = true, description = "Group name exactly as recorded, taken from the groups list in "
                    + "jdbc_overview.")
            String group) {

        // Insisted on rather than passed through: the manager reads a null group as "no filter", so an
        // omitted one would return the whole statement dashboard under the heading of one group.
        String name = ToolArguments.required(group, "group", NO_GROUP_RECOVERY);

        if (DashboardFeature.missing(profileManager, FeatureType.JDBC_STATEMENTS_DASHBOARD)) {
            return NO_STATEMENT_DATA;
        }

        JdbcOverviewData data = profileManager.custom().jdbcStatementManager().overviewData(name);
        if (data.groups().isEmpty() && data.slowStatements().isEmpty()) {
            return McpToolOutput.error(NO_SUCH_GROUP.formatted(name));
        }

        return LinkedOutput.json(
                dashboard(data, UiLinks.view(profileId(), STATEMENT_GROUPS_VIEW, groupQuery(name))));
    }

    @Tool(description = "The JDBC connection pools: their configured minimum and maximum sizes against "
            + "the peak and average connections actually used, how many threads waited for a "
            + "connection, and how often acquisition timed out. Read this when requests are slow but "
            + "the statements are not - exhaustion here looks like slowness everywhere else.")
    public String pools() {
        if (DashboardFeature.missing(profileManager, FeatureType.JDBC_POOL_DASHBOARD)) {
            return NO_POOL_DATA;
        }

        List<JdbcPoolData> pools = profileManager.custom().jdbcPoolManager().allPoolsData();
        return LinkedOutput.json(new JdbcPools(
                pools,
                NextSteps.builder()
                        .add(STEP_STATEMENTS)
                        .when(contentionOccurred(pools), STEP_POOL_WAITS)
                        .build(),
                UiLinks.view(profileId(), POOL_VIEW)));
    }

    private JdbcDashboard dashboard(JdbcOverviewData data, String uiLink) {
        return new JdbcDashboard(
                data.header(),
                data.operations(),
                ToolArguments.firstOf(data.groups(), MAX_GROUPS),
                data.slowStatements().stream().map(JdbcMcpTools::readable).toList(),
                NextSteps.builder()
                        .add(STEP_GROUP)
                        .when(data.header().errorCount() > 0, STEP_STATEMENT_ERRORS)
                        .add(STEP_POOLS)
                        .build(),
                uiLink);
    }

    /**
     * Whether any thread ever waited for a connection or was refused one - the event happening at all
     * is what makes the pool worth explaining, not how often it happened.
     */
    private static boolean contentionOccurred(List<JdbcPoolData> pools) {
        return pools.stream().anyMatch(pool ->
                pool.statistics().timeoutsCount() > 0 || pool.statistics().maxPendingThreadCount() > 0);
    }

    /**
     * The same statement with its two open-ended text fields shortened.
     */
    private static SlowStatement readable(JdbcSlowStatement statement) {
        return new SlowStatement(
                statement.timestamp(),
                truncate(statement.sql(), MAX_SQL_CHARS),
                statement.statementName(),
                statement.statementGroup(),
                statement.operation(),
                statement.executionTime(),
                statement.rowsProcessed(),
                truncate(statement.parameters(), MAX_PARAMETERS_CHARS),
                statement.isSuccess(),
                statement.isBatch(),
                statement.isLob());
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + TRUNCATION_SUFFIX;
    }

    private Map<String, String> groupQuery(String group) {
        Map<String, String> query = UiLinks.query();
        query.put(GROUP_PARAM, group);
        return query;
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record JdbcDashboard(
            JdbcHeader header,
            List<JdbcOperationStats> operations,
            List<JdbcGroup> groups,
            List<SlowStatement> slowStatements,
            List<String> nextSteps,
            String uiLink) {
    }

    private record JdbcPools(List<JdbcPoolData> pools, List<String> nextSteps, String uiLink) {
    }

    private record SlowStatement(
            long timestamp,
            String sql,
            String statementName,
            String statementGroup,
            String operation,
            long executionTime,
            long rowsProcessed,
            String parameters,
            boolean success,
            boolean batch,
            boolean lob) {
    }
}
