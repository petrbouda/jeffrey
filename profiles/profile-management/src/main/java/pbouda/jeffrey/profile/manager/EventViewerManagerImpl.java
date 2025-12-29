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

package pbouda.jeffrey.profile.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.shared.model.EventSummary;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.profile.common.treetable.EventViewerData;
import pbouda.jeffrey.profile.common.treetable.Tree;
import pbouda.jeffrey.profile.common.treetable.TreeData;
import pbouda.jeffrey.provider.api.model.FieldDescription;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;

import java.util.List;

public class EventViewerManagerImpl implements EventViewerManager {

    private final ProfileEventRepository eventRepository;
    private final ProfileEventTypeRepository eventTypeRepository;

    public EventViewerManagerImpl(
            ProfileEventRepository eventRepository,
            ProfileEventTypeRepository eventTypeRepository) {

        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
    }

    @Override
    public JsonNode eventTypesTree() {
        Tree tree = new Tree();

        List<EventSummary> summaries = eventTypeRepository.eventSummaries();
        for (EventSummary eventSummary : summaries) {
            TreeData data = new EventViewerData(
                    eventSummary.categories(),
                    eventSummary.label(),
                    eventSummary.name(),
                    eventSummary.samples(),
                    eventSummary.source().getLabel(),
                    eventSummary.hasStacktrace());
            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    @Override
    public List<EventViewerData> eventTypes() {
        return eventTypeRepository.eventSummaries().stream()
                .map(summary -> new EventViewerData(
                        summary.categories(),
                        summary.label(),
                        summary.name(),
                        summary.samples(),
                        summary.source().getLabel(),
                        summary.hasStacktrace())
                ).toList();
    }


    @Override
    public List<JsonNode> events(Type eventType) {
        return eventRepository.eventsByTypeWithFields(eventType);
    }

    @Override
    public List<FieldDescription> eventColumns(Type eventType) {
        return eventTypeRepository.eventColumns(eventType);
    }
}
