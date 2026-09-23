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
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.timeseries.SingleSerie;

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
