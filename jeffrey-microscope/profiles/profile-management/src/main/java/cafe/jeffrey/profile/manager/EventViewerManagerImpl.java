/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.profile.common.treetable.Tree;
import cafe.jeffrey.profile.common.treetable.TreeData;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;

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
