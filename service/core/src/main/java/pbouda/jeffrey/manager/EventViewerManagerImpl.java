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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.treetable.EventViewerData;
import pbouda.jeffrey.common.treetable.Tree;
import pbouda.jeffrey.common.treetable.TreeData;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.List;

public class EventViewerManagerImpl implements EventViewerManager {

    private final ProfileEventRepository eventsReadRepository;

    public EventViewerManagerImpl(ProfileEventRepository eventsReadRepository) {
        this.eventsReadRepository = eventsReadRepository;
    }

    @Override
    public JsonNode allEventTypes() {
        Tree tree = new Tree();

        List<EventSummary> summaries = eventsReadRepository.eventSummaries();
        for (EventSummary eventSummary : summaries) {
            TreeData data = new EventViewerData(
                    eventSummary.categories(),
                    eventSummary.label(),
                    eventSummary.name(),
                    eventSummary.samples(),
                    eventSummary.hasStacktrace());
            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    @Override
    public List<JsonNode> events(Type eventType) {
        return eventsReadRepository.eventsByTypeWithFields(eventType);
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        return eventsReadRepository.eventColumns(eventType);
    }
}
