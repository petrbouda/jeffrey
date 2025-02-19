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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.manager.EventViewerManager;

import java.util.List;

public class EventViewerResource {

    private final EventViewerManager eventViewerManager;

    public EventViewerResource(EventViewerManager eventViewerManager) {
        this.eventViewerManager = eventViewerManager;
    }

    @GET
    public JsonNode generate() {
        return eventViewerManager.allEventTypes();
    }

    @GET
    @Path("/events/{eventType}")
    public List<JsonNode> getEvents(@PathParam("eventType") Type eventType) {
        return eventViewerManager.events(eventType);
    }

    @GET
    @Path("/events/{eventType}/columns")
    public JsonNode getEventColumns(@PathParam("eventType") Type eventType) {
        return eventViewerManager.eventColumns(eventType);
    }
}
