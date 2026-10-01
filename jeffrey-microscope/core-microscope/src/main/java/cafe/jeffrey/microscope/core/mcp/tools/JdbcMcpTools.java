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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.PoolConfiguration;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.PoolEventStatistics;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.PoolStatistics;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcGroup;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcHeader;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOperationStats;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcStatementNameStats;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
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

    private static final MicroscopeView STATEMENTS_VIEW = MicroscopeView.JDBC_STATEMENTS;
    private static final MicroscopeView STATEMENT_GROUPS_VIEW = MicroscopeView.JDBC_STATEMENT_GROUPS;
    private static final MicroscopeView POOL_VIEW = MicroscopeView.JDBC_POOL;
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

    private static final String GROUP_WHY = "the costliest group on its own: its percentiles and slowest statements";
    private static final String POOLS_WHY =
            "whether the time went waiting for a connection rather than running SQL";
    private static final String STATEMENTS_WHY = "the statements these connections carried";
    private static final String STATEMENT_ERRORS_WHY =
            "what the application reported about the failed statements, when this profile carries traces";
    private static final String POOL_WAITS_WHY =
            "what the application said about the connection waits, when this profile carries traces";
    private static final String POOL_WAIT_REQUESTS_WHY =
            "the requests that paid for the connection waits, when this profile carries traces";

    private static final String NO_SUCH_GROUP =
            "No statements were recorded for group '%s'. Call jdbc_overview and take a name from its "
                    + "groups list.";

    private static final String NO_GROUP_RECOVERY =
            "Call jdbc_overview and take a name from its groups list.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public JdbcMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the JDBC statement dashboard: statement count, execution-time "
            + "percentiles in nanoseconds, success rate and error count, the operation mix "
            + "(SELECT/INSERT/UPDATE/...), the statement groups ranked by cost (the costliest 40, "
            + "with omittedGroups counting the rest), and the slowest individual statements with "
            + "their SQL and UTC epoch-millisecond instant. Answers 'is the database the "
            + "bottleneck'. status NOT_RECORDED: the recording did not capture the JDBC events.")
    @McpOutputSchema(JdbcDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview() {
        String uiLink = UiLinks.view(profileId(), STATEMENTS_VIEW);
        if (statementsNotRecorded()) {
            return McpToolResult.of(JdbcDashboard.notRecorded(profileId(), null, statementsFollowUp(), uiLink));
        }

        JdbcOverviewData data = profileManager.custom().jdbcStatementManager().overviewData();
        List<JdbcGroup> shown = ToolArguments.firstOf(data.groups(), MAX_GROUPS);
        NextSteps.Builder steps = NextSteps.builder(advertised);
        if (!shown.isEmpty()) {
            steps.next(call(FollowUpCalls.JDBC_STATEMENT_GROUP)
                    .with(FollowUpCalls.GROUP, shown.getFirst().group())
                    .why(GROUP_WHY));
        }
        return McpToolResult.of(dashboard(data, null, shown, steps, uiLink));
    }

    @Tool(description = "Returns one statement group in detail: the same percentiles and slowest "
            + "statements as jdbc_overview, narrowed to a single group. An unknown group is an "
            + "error naming it. status NOT_RECORDED: the recording did not capture the JDBC events.")
    @McpOutputSchema(JdbcDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult statementGroup(
            @ToolParam(required = true, description = "Group name exactly as recorded, taken from the groups list in "
                    + "jdbc_overview.")
            String group) {

        // Insisted on rather than passed through: the manager reads a null group as "no filter", so an
        // omitted one would return the whole statement dashboard under the heading of one group.
        String name = ToolArguments.required(group, "group", NO_GROUP_RECOVERY);
        String uiLink = UiLinks.view(profileId(), STATEMENT_GROUPS_VIEW, groupQuery(name));
        if (statementsNotRecorded()) {
            return McpToolResult.of(JdbcDashboard.notRecorded(profileId(), name, statementsFollowUp(), uiLink));
        }

        JdbcOverviewData data = profileManager.custom().jdbcStatementManager().overviewData(name);
        if (data.groups().isEmpty() && data.slowStatements().isEmpty()) {
            throw new ToolExecutionException(NO_SUCH_GROUP.formatted(name));
        }

        List<JdbcGroup> shown = ToolArguments.firstOf(data.groups(), MAX_GROUPS);
        return McpToolResult.of(dashboard(data, name, shown, NextSteps.builder(advertised), uiLink));
    }

    @Tool(description = "Returns the JDBC connection pools: their configured minimum and maximum sizes "
            + "against the peak and average connections actually used, how many threads waited for a "
            + "connection, how often acquisition timed out, and each pool event's count and "
            + "durations in nanoseconds. Answers 'requests are slow but the statements are not' - "
            + "pool exhaustion looks like slowness everywhere else. status NOT_RECORDED: the "
            + "recording did not capture the pooled-connection events.")
    @McpOutputSchema(JdbcPools.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult pools() {
        String uiLink = UiLinks.view(profileId(), POOL_VIEW);
        if (DashboardFeature.missing(profileManager, FeatureType.JDBC_POOL_DASHBOARD)) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .nextWhen(!statementsNotRecorded(), call(FollowUpCalls.JDBC_OVERVIEW).why(STATEMENTS_WHY))
                    .followUp();
            return McpToolResult.of(new JdbcPools(
                    DashboardStatus.NOT_RECORDED, NO_POOL_DATA, profileId(), List.of(), followUp, uiLink));
        }

        List<JdbcPoolData> pools = profileManager.custom().jdbcPoolManager().allPoolsData();
        boolean contended = contentionOccurred(pools);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(!statementsNotRecorded(), call(FollowUpCalls.JDBC_OVERVIEW).why(STATEMENTS_WHY))
                .nextWhen(contended, call(FollowUpCalls.TRACES_NOTIFICATIONS).why(POOL_WAITS_WHY))
                .nextWhen(contended, call(FollowUpCalls.TRACES_OPERATIONS).why(POOL_WAIT_REQUESTS_WHY))
                .followUp();
        return McpToolResult.of(new JdbcPools(DashboardStatus.OK, null, profileId(),
                pools.stream().map(Pool::of).toList(), followUp, uiLink));
    }

    /**
     * The statement dashboard, for the whole profile or for one group. A group's answer does not route
     * to itself, so the caller passes the steps that belong to its own question first.
     */
    private JdbcDashboard dashboard(
            JdbcOverviewData data, String group, List<JdbcGroup> shown, NextSteps.Builder steps, String uiLink) {

        McpFollowUp followUp = steps
                .nextWhen(data.header().errorCount() > 0,
                        call(FollowUpCalls.TRACES_NOTIFICATIONS).why(STATEMENT_ERRORS_WHY))
                .nextWhen(!DashboardFeature.missing(profileManager, FeatureType.JDBC_POOL_DASHBOARD),
                        call(FollowUpCalls.JDBC_POOLS).why(POOLS_WHY))
                .followUp();
        return new JdbcDashboard(
                DashboardStatus.OK,
                null,
                profileId(),
                group,
                JdbcTotals.of(data.header()),
                data.operations(),
                shown.stream().map(StatementGroup::of).toList(),
                data.groups().size() - shown.size(),
                data.slowStatements().stream().map(SlowStatement::of).toList(),
                followUp,
                uiLink);
    }

    /** Statements that were not recorded: the pools, when those were. */
    private McpFollowUp statementsFollowUp() {
        return NextSteps.builder(advertised)
                .nextWhen(!DashboardFeature.missing(profileManager, FeatureType.JDBC_POOL_DASHBOARD),
                        call(FollowUpCalls.JDBC_POOLS).why(POOLS_WHY))
                .followUp();
    }

    private boolean statementsNotRecorded() {
        return DashboardFeature.missing(profileManager, FeatureType.JDBC_STATEMENTS_DASHBOARD);
    }

    private McpNextTool.Call call(String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    /**
     * Whether any thread ever waited for a connection or was refused one - the event happening at all
     * is what makes the pool worth explaining, not how often it happened.
     */
    private static boolean contentionOccurred(List<JdbcPoolData> pools) {
        return pools.stream().anyMatch(pool ->
                pool.statistics().timeoutsCount() > 0 || pool.statistics().maxPendingThreadCount() > 0);
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

    /** The statements' totals, with every duration in nanoseconds. */
    record JdbcTotals(
            long statementCount,
            long maxExecutionTimeNanos,
            long p99ExecutionTimeNanos,
            long p95ExecutionTimeNanos,
            @McpDescription("Share of statements that did not fail, as the dashboard computes it")
            double successRate,
            long errorCount) {

        static JdbcTotals of(JdbcHeader header) {
            return new JdbcTotals(header.statementCount(), header.maxExecutionTime(), header.p99ExecutionTime(),
                    header.p95ExecutionTime(), Figures.number(header.successRate()), header.errorCount());
        }
    }

    /** One statement name inside a group. */
    record StatementName(String name, long count, long p99ExecutionTimeNanos) {

        static StatementName of(JdbcStatementNameStats stats) {
            return new StatementName(stats.label(), stats.value(), stats.p99ExecutionTime());
        }
    }

    /** One statement group, with every duration in nanoseconds. */
    record StatementGroup(
            String group,
            long count,
            long totalRowsProcessed,
            long totalExecutionTimeNanos,
            long maxExecutionTimeNanos,
            long p99ExecutionTimeNanos,
            long p95ExecutionTimeNanos,
            long errorCount,
            List<StatementName> statementNames) {

        static StatementGroup of(JdbcGroup group) {
            return new StatementGroup(group.group(), group.count(), group.totalRowsProcessed(),
                    group.totalExecutionTime(), group.maxExecutionTime(), group.p99ExecutionTime(),
                    group.p95ExecutionTime(), group.errorCount(),
                    group.statementNames().stream().map(StatementName::of).toList());
        }
    }

    /**
     * One slow statement, with its two open-ended text fields shortened. The event builder reads SQL and
     * parameters with no default, so either may be absent.
     */
    record SlowStatement(
            @McpDescription("When the statement started, as UTC epoch milliseconds")
            long atEpochMs,
            @McpNullable
            @McpDescription("The SQL, cut to 500 characters with a trailing '...'; null when not recorded")
            String sql,
            String statementName,
            String statementGroup,
            String operation,
            long executionTimeNanos,
            long rowsProcessed,
            @McpNullable
            @McpDescription("The bound parameters, cut to 200 characters; null when not recorded")
            String parameters,
            boolean success,
            boolean batch,
            boolean lob) {

        static SlowStatement of(JdbcSlowStatement statement) {
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
    }

    /**
     * The statement dashboard, or one group of it, minus its two chart series.
     *
     * @param group the group asked about; null for the whole dashboard
     */
    record JdbcDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            @McpNullable
            @McpDescription("The group asked about; null for jdbc_overview")
            String group,
            @McpNullable
            @McpDescription("The statements' totals; null when status is NOT_RECORDED")
            JdbcTotals header,
            List<JdbcOperationStats> operations,
            @McpDescription("The costliest statement groups, at most 40")
            List<StatementGroup> groups,
            @McpNullable
            @McpDescription("Groups left out of groups by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedGroups,
            List<SlowStatement> slowStatements,
            McpFollowUp followUp,
            @McpDescription("The statement page in the Microscope UI, for the user")
            String uiLink) {

        static JdbcDashboard notRecorded(String profileId, String group, McpFollowUp followUp, String uiLink) {
            return new JdbcDashboard(DashboardStatus.NOT_RECORDED, NO_STATEMENT_DATA, profileId, group, null,
                    List.of(), List.of(), null, List.of(), followUp, uiLink);
        }
    }

    /** A pool's usage; the ratios are the dashboard's own, the averages over the recording. */
    record PoolUsage(
            int peakConnectionCount,
            int peakActiveConnectionCount,
            double avgActiveConnectionCount,
            int maxPendingThreadCount,
            @McpDescription("Share of the recording during which some thread waited for a connection, in percent")
            double pendingPeriodsPercent,
            long timeoutsCount,
            @McpDescription("Acquisitions that timed out, in percent of those acquired")
            double timeoutRatePercent) {

        static PoolUsage of(PoolStatistics statistics) {
            return new PoolUsage(statistics.peakConnectionCount(), statistics.peakActiveConnectionCount(),
                    Figures.number(statistics.avgActiveConnectionCount()), statistics.maxPendingThreadCount(),
                    Figures.number(statistics.pendingPeriodsPercent()), statistics.timeoutsCount(),
                    Figures.number(statistics.timeoutRate()));
        }
    }

    /** One kind of pool event, with its durations in nanoseconds. */
    record PoolEvent(
            String eventName,
            String eventType,
            long count,
            long minNanos,
            long maxNanos,
            long avgNanos) {

        static PoolEvent of(PoolEventStatistics event) {
            return new PoolEvent(event.eventName(), event.eventType(), event.count(), event.min(), event.max(),
                    event.avg());
        }
    }

    /** One connection pool. */
    record Pool(
            String poolName,
            PoolConfiguration configuration,
            PoolUsage statistics,
            List<PoolEvent> events) {

        static Pool of(JdbcPoolData pool) {
            return new Pool(pool.poolName(), pool.configuration(), PoolUsage.of(pool.statistics()),
                    pool.eventStatistics().stream().map(PoolEvent::of).toList());
        }
    }

    record JdbcPools(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no pool dashboard; null when status is OK")
            String reason,
            String profileId,
            List<Pool> pools,
            McpFollowUp followUp,
            @McpDescription("The connection-pool page in the Microscope UI, for the user")
            String uiLink) {
    }
}
