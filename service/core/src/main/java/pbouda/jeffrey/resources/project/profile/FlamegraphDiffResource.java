
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

package pbouda.jeffrey.resources.project.profile;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.FlamegraphManager;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.resources.request.GenerateFlamegraphRequest;

import java.util.List;

public class FlamegraphDiffResource {

    private final FlamegraphManager diffFlamegraphManager;

    public FlamegraphDiffResource(FlamegraphManager diffFlamegraphManager) {
        this.diffFlamegraphManager = diffFlamegraphManager;
    }

    @POST
    public ObjectNode generate(GenerateFlamegraphRequest request) {
        return diffFlamegraphManager.generate(FlamegraphResource.mapToGenerateRequest(request));
    }

    @POST
    @Path("/save")
    public void save(GenerateFlamegraphRequest request) {
        diffFlamegraphManager.save(FlamegraphResource.mapToGenerateRequest(request), request.flamegraphName());
    }

    @GET
    @Path("/events")
    public List<EventSummaryResult> events() {
        return diffFlamegraphManager.eventSummaries();
    }

//    @POST
//    @Path("/export")
//    public void exportDiff(ExportRequest request) {
//        diffFlamegraphManager.export(request.eventType(), request.timeRange(), false);
//    }
}
