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

package pbouda.jeffrey.profile.resources.custom;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.manager.custom.JdbcPoolManager;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class JdbcPoolResource {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcPoolResource.class);

    public record TimeseriesRequest(String poolName, Type eventType) {
    }

    private final JdbcPoolManager jdbcPoolManager;

    public JdbcPoolResource(JdbcPoolManager jdbcPoolManager) {
        this.jdbcPoolManager = jdbcPoolManager;
    }

    @GET
    public List<JdbcPoolData> allPoolsData() {
        LOG.debug("Fetching JDBC pool data");
        return jdbcPoolManager.allPoolsData();
    }

    @POST
    @Path("/timeseries")
    public SingleSerie timeseries(TimeseriesRequest request) {
        LOG.debug("Fetching JDBC pool timeseries");
        return jdbcPoolManager.timeseries(request.poolName, request.eventType);
    }
}
