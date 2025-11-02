/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.manager.custom.builder.JdbcOverviewEventBuilder;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcOverviewData;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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

        return eventStreamRepository.newEventStreamerFactory(configurer)
                .newGenericStreamer()
                .startStreaming(new JdbcOverviewEventBuilder(timeRange, MAX_SLOW_REQUESTS, statementFilter));
    }
}
