/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.manager.EventViewerManager;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.microscope.model.Type;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/viewer")
public class EventViewerController {

    private static final Logger LOG = LoggerFactory.getLogger(EventViewerController.class);

    private final ProfileManagerResolver resolver;

    public EventViewerController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/events/types/tree")
    public JsonNode eventTypesTree(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching event types tree");
        return mgr(profileId).eventTypesTree();
    }

    @GetMapping("/events/types")
    public List<EventViewerData> eventTypes(@PathVariable("profileId") String profileId) {
        var result = mgr(profileId).eventTypes();
        LOG.debug("Listed event types: profileId={} count={}", profileId, result.size());
        return result;
    }

    @GetMapping("/events/{eventType}")
    public List<JsonNode> events(
            @PathVariable("profileId") String profileId,
            @PathVariable("eventType") Type eventType) {
        LOG.debug("Fetching events: eventType={}", eventType);
        return mgr(profileId).events(eventType);
    }

    @GetMapping("/events/{eventType}/columns")
    public List<FieldDescription> getEventColumns(
            @PathVariable("profileId") String profileId,
            @PathVariable("eventType") Type eventType) {
        LOG.debug("Fetching event columns: eventType={}", eventType);
        return mgr(profileId).eventColumns(eventType);
    }

    private EventViewerManager mgr(String profileId) {
        return resolver.resolve(profileId).eventViewerManager();
    }
}
