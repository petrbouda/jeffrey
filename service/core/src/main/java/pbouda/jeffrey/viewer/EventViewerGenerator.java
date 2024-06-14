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

package pbouda.jeffrey.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;

import java.nio.file.Path;

public interface EventViewerGenerator {

    /**
     * Generates a JSON entity for <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a> containing
     * all event types available for the current profile.
     *
     * @param path path to the JFR recording file
     * @return all event types for the current profile in the format of PrimeVue TreeTable
     */
    JsonNode allEventTypes(Path path);

    /**
     * Generates and provides all events of the given type.
     *
     * @param path path to the JFR recording file
     * @param eventType type of the events to be fetched from the recording
     * @return events in JSON format.
     */
    JsonNode events(Path path, Type eventType);

    /**
     * Generates the structure of the given event type to be able to generate a table in UI.
     *
     * @param path path to the JFR recording file
     * @param eventType type of the events to be fetched from the recording
     * @return event structure in JSON format.
     */
    JsonNode eventColumns(Path path, Type eventType);

}
