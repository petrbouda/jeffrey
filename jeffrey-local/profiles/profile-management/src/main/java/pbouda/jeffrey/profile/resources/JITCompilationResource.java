/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
import pbouda.jeffrey.profile.manager.JITCompilationManager;
import pbouda.jeffrey.profile.common.event.JITCompilationStats;
import pbouda.jeffrey.profile.common.event.JITLongCompilation;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class JITCompilationResource {

    private static final Logger LOG = LoggerFactory.getLogger(JITCompilationResource.class);
    private static final int MAX_COMPILATIONS = 20;

    private final JITCompilationManager jitCompilationManager;

    public JITCompilationResource(JITCompilationManager jitCompilationManager) {
        this.jitCompilationManager = jitCompilationManager;
    }

    @GET
    @Path("/statistics")
    public JITCompilationStats statistics() {
        LOG.debug("Fetching JIT compilation statistics");
        return jitCompilationManager.statistics();
    }

    @GET
    @Path("/compilations")
    public List<JITLongCompilation> compilations() {
        LOG.debug("Fetching JIT compilations");
        return jitCompilationManager.compilations(MAX_COMPILATIONS);
    }

    @GET
    @Path("/timeseries")
    public SingleSerie timeseries() {
        LOG.debug("Fetching JIT compilation timeseries");
        return jitCompilationManager.timeseries();
    }
}
