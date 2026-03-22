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
import pbouda.jeffrey.profile.manager.HeapMemoryManager;
import pbouda.jeffrey.profile.manager.model.heap.HeapMemoryOverviewData;
import pbouda.jeffrey.profile.manager.model.heap.HeapMemoryTimeseriesType;
import pbouda.jeffrey.timeseries.SingleSerie;

public class HeapMemoryResource {

    private static final Logger LOG = LoggerFactory.getLogger(HeapMemoryResource.class);

    private final HeapMemoryManager heapMemoryManager;

    public HeapMemoryResource(HeapMemoryManager heapMemoryManager) {
        this.heapMemoryManager = heapMemoryManager;
    }

    @GET
    public HeapMemoryOverviewData overviewData() {
        LOG.debug("Fetching heap memory overview");
        return heapMemoryManager.getOverviewData();
    }

    @GET
    @Path("timeseries")
    public SingleSerie timeseries(@QueryParam("timeseriesType") HeapMemoryTimeseriesType timeseriesType) {
        LOG.debug("Fetching heap memory timeseries: type={}", timeseriesType);
        return heapMemoryManager.timeseries(timeseriesType);
    }
}
