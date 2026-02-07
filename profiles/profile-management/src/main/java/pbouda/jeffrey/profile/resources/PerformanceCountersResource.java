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

package pbouda.jeffrey.profile.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.manager.AdditionalFilesManager;
import pbouda.jeffrey.profile.manager.model.PerfCounter;

import java.util.List;

public class PerformanceCountersResource {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceCountersResource.class);

    private final AdditionalFilesManager additionalFilesManager;

    public PerformanceCountersResource(AdditionalFilesManager additionalFilesManager) {
        this.additionalFilesManager = additionalFilesManager;
    }

    @GET
    @Path("/exists")
    public boolean exists() {
        LOG.debug("Checking performance counters existence");
        return additionalFilesManager.performanceCountersExists();
    }

    @GET
    public List<PerfCounter> performanceCounters() {
        LOG.debug("Fetching performance counters");
        return additionalFilesManager.performanceCounters();
    }
}
