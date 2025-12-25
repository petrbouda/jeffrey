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

package pbouda.jeffrey.profile.manager.custom;

import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;
import java.util.function.Function;

public interface JdbcStatementManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, JdbcStatementManager> {
    }

    JdbcOverviewData overviewData();

    JdbcOverviewData overviewData(String group);

    List<SingleSerie> timeseries(String group, String statementName);

    List<JdbcSlowStatement> slowStatements(String group, String statementName);
}
