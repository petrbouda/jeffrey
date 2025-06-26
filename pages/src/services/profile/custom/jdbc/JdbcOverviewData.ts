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

import JdbcHeader from "@/services/profile/custom/jdbc/JdbcHeader.ts";
import JdbcStatementInfo from "@/services/profile/custom/jdbc/JdbcStatementInfo.ts";
import JdbcOperationStats from "@/services/profile/custom/jdbc/JdbcOperationStats.ts";
import JdbcSlowStatement from "@/services/profile/custom/jdbc/JdbcSlowStatement.ts";
import JdbcGroup from "@/services/profile/custom/jdbc/JdbcGroup.ts";
import Serie from "@/services/timeseries/model/Serie.ts";

export default class JdbcOverviewData {
    constructor(
        public header: JdbcHeader,
        public statements: JdbcStatementInfo[],
        public operations: JdbcOperationStats[],
        public slowStatements: JdbcSlowStatement[],
        public groups: JdbcGroup[],
        public executionTimeSerie: Serie,
        public statementCountSerie: Serie,
    ) {}
}
