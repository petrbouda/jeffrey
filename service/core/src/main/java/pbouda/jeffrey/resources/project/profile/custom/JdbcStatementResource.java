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

package pbouda.jeffrey.resources.project.profile.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pbouda.jeffrey.manager.custom.JdbcStatementManager;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcOverviewData;
import pbouda.jeffrey.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.net.URLDecoder;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JdbcStatementResource {

    private final JdbcStatementManager jdbcStatementManager;

    public JdbcStatementResource(JdbcStatementManager jdbcStatementManager) {
        this.jdbcStatementManager = jdbcStatementManager;
    }

    @GET
    public JdbcOverviewData overviewData() {
        return jdbcStatementManager.overviewData();
    }

    @GET
    @Path("single")
    public JdbcOverviewData singleUriData(@QueryParam("group") String group) {
        String decoded = URLDecoder.decode(group, UTF_8);
        return jdbcStatementManager.overviewData(decoded);
    }

    @GET
    @Path("timeseries")
    public List<SingleSerie> specificTimeSeries(
            @QueryParam("group") String group,
            @QueryParam("statementName") String statementName) {

        String decodedGroup = URLDecoder.decode(group, UTF_8);
        String decodedName = URLDecoder.decode(statementName, UTF_8);
        return jdbcStatementManager.timeseries(decodedGroup, decodedName);
    }

    @GET
    @Path("slowest")
    public List<JdbcSlowStatement> specificSlowest(
            @QueryParam("group") String group,
            @QueryParam("statementName") String statementName) {

        String decodedGroup = URLDecoder.decode(group, UTF_8);
        String decodedName = URLDecoder.decode(statementName, UTF_8);
        return jdbcStatementManager.slowStatements(decodedGroup, decodedName);
    }
}
