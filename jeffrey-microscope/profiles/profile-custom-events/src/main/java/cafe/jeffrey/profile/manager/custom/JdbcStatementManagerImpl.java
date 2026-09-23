/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.custom.builder.JdbcOverviewEventBuilder;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;
import java.util.function.BiPredicate;

public class JdbcStatementManagerImpl implements JdbcStatementManager {

    private static final int MAX_SLOW_REQUESTS = 20;

    private static final List<Type> JDBC_STATEMENT_TYPES = List.of(
            Type.JDBC_INSERT,
            Type.JDBC_UPDATE,
            Type.JDBC_DELETE,
            Type.JDBC_QUERY,
            Type.JDBC_EXECUTE,
            Type.JDBC_STREAM);

    private final ProfileInfo profileInfo;
    private final ProfileEventStreamRepository eventStreamRepository;

    public JdbcStatementManagerImpl(ProfileInfo profileInfo, ProfileEventStreamRepository eventStreamRepository) {
        this.profileInfo = profileInfo;
        this.eventStreamRepository = eventStreamRepository;
    }

    @Override
    public JdbcOverviewData overviewData() {
        return _overviewData(null, null);
    }

    @Override
    public JdbcOverviewData overviewData(String group) {
        return _overviewData(group, null);
    }

    @Override
    public List<SingleSerie> timeseries(String group, String statementName) {
        JdbcOverviewData jdbcOverviewData = _overviewData(group, statementName);
        return List.of(
                jdbcOverviewData.executionTimeSerie(),
                jdbcOverviewData.statementCountSerie());
    }

    @Override
    public List<JdbcSlowStatement> slowStatements(String group, String statementName) {
        JdbcOverviewData jdbcOverviewData = _overviewData(group, statementName);
        return jdbcOverviewData.slowStatements();
    }

    private JdbcOverviewData _overviewData(String group, String statementName) {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypeInfo()
                .withEventTypes(JDBC_STATEMENT_TYPES)
                .withTimeRange(timeRange)
                .withJsonFields();

        BiPredicate<String, String> statementFilter = null;
        if (group != null && statementName != null) {
            statementFilter = (g, s) -> g.equals(group) && s.equalsIgnoreCase(statementName);
        } else if (group != null) {
            statementFilter = (g, _) -> g.equals(group);
        } else if (statementName != null) {
            statementFilter = (_, s) -> s.equalsIgnoreCase(statementName);
        }

        return eventStreamRepository.genericStreaming(
                configurer, new JdbcOverviewEventBuilder(timeRange, MAX_SLOW_REQUESTS, statementFilter));
    }
}
