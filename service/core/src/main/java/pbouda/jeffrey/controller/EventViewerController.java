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

package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GetEventsRequest;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.EventViewerManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.TimeseriesManager;

import java.io.IOException;

@RestController
@RequestMapping("/viewer")
public class EventViewerController {

    private final ProfilesManager profilesManager;

    public EventViewerController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/all")
    public JsonNode generate(@RequestBody ProfileIdRequest request) {
        EventViewerManager eventViewerManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::eventViewerManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return eventViewerManager.allEventTypes();
    }

    @PostMapping("/events")
    public JsonNode getEvents(@RequestBody GetEventsRequest request) throws IOException {
        EventViewerManager eventViewerManager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::eventViewerManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return eventViewerManager.events(request.eventType());
    }

    @PostMapping("/events/columns")
    public JsonNode getEventColumns(@RequestBody GetEventsRequest request) {
        EventViewerManager eventViewerManager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::eventViewerManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return eventViewerManager.eventColumns(request.eventType());
    }

    @PostMapping("/events/timeseries")
    public JsonNode getTimeseriesForEvent(@RequestBody GetEventsRequest request) {
        TimeseriesManager manager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::timeseriesManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.contentByEventType(request.eventType());
    }
}
