/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.local.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.manager.EventViewerManager;
import cafe.jeffrey.provider.profile.model.FieldDescription;
import cafe.jeffrey.shared.common.model.Type;
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
