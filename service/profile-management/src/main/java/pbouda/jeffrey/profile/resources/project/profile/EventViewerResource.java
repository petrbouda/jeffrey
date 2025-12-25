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

package pbouda.jeffrey.profile.resources.project.profile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.treetable.EventViewerData;
import pbouda.jeffrey.profile.manager.EventViewerManager;
import pbouda.jeffrey.provider.api.model.FieldDescription;

import java.util.List;

public class EventViewerResource {

    private final EventViewerManager eventViewerManager;

    public EventViewerResource(EventViewerManager eventViewerManager) {
        this.eventViewerManager = eventViewerManager;
    }

    @GET
    @Path("/events/types/tree")
    public JsonNode eventTypesTree() {
        return eventViewerManager.eventTypesTree();
    }

    @GET
    @Path("/events/types")
    public List<EventViewerData> eventTypes() {
        return eventViewerManager.eventTypes();
    }

    @GET
    @Path("/events/{eventType}")
    public List<JsonNode> events(@PathParam("eventType") Type eventType) {
        return eventViewerManager.events(eventType);
    }

    @GET
    @Path("/events/{eventType}/columns")
    public List<FieldDescription> getEventColumns(@PathParam("eventType") Type eventType) {
        return eventViewerManager.eventColumns(eventType);
    }
}
