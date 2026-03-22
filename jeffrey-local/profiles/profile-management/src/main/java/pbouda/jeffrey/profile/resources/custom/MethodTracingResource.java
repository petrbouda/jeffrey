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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.manager.custom.MethodTracingManager;
import pbouda.jeffrey.profile.manager.custom.model.method.CumulationMode;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import pbouda.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;

public class MethodTracingResource {

    private static final Logger LOG = LoggerFactory.getLogger(MethodTracingResource.class);

    private final MethodTracingManager manager;

    public MethodTracingResource(MethodTracingManager manager) {
        this.manager = manager;
    }

    @GET
    @Path("/overview")
    public MethodTracingOverviewData overview() {
        LOG.debug("Fetching method tracing overview");
        return manager.overview();
    }

    @GET
    @Path("/slowest")
    public MethodTracingSlowestData slowest() {
        LOG.debug("Fetching method tracing slowest");
        return manager.slowest();
    }

    @GET
    @Path("/cumulated")
    public MethodTracingCumulatedData cumulated(@QueryParam("mode") String mode) {
        LOG.debug("Fetching method tracing cumulated: mode={}", mode);
        return manager.cumulated(CumulationMode.fromString(mode));
    }
}
