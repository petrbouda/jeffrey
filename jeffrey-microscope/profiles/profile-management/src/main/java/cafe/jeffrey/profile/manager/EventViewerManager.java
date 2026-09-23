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
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.common.treetable.EventViewerData;
import cafe.jeffrey.provider.profile.api.FieldDescription;

import java.util.List;
import java.util.function.Function;

public interface EventViewerManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, EventViewerManager> {
    }

    /**
     * Generates a JSON entity for <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a> containing
     * all event types available for the current profile.
     *
     * @return all event types for the current profile in the format of PrimeVue TreeTable
     */
    JsonNode eventTypesTree();

    /**
     * Returns a list of all event types available for the current profile.
     *
     * @return all event types for the current profile
     */
    List<EventViewerData> eventTypes();

    /**
     * Generates and provides all events of the given type.
     *
     * @param eventType type of the events to be fetched from the recording
     * @return events in JSON format.
     */
    List<JsonNode> events(Type eventType);

    /**
     * Generates the structure of the given event type to be able to generate a table in UI.
     *
     * @param eventType type of the events to be fetched from the recording
     * @return event structure in JSON format.
     */
    List<FieldDescription> eventColumns(Type eventType);
}
