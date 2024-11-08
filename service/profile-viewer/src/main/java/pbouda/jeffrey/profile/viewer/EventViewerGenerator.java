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

package pbouda.jeffrey.profile.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Type;

import java.nio.file.Path;
import java.util.List;

public interface EventViewerGenerator {

    /**
     * Generates a JSON entity for <a href="https://primevue.org/treetable/#template">PrimeVue TreeTable</a> containing
     * all event types available for the current profile.
     *
     * @param recordings collection of paths to the JFR recording files
     * @return all event types for the current profile in the format of PrimeVue TreeTable
     */
    JsonNode allEventTypes(List<Path> recordings);

    /**
     * Generates and provides all events of the given type.
     *
     * @param recordings collection of paths to the JFR recording files
     * @param eventType type of the events to be fetched from the recording
     * @return events in JSON format.
     */
    JsonNode events(List<Path> recordings, Type eventType);

    /**
     * Generates the structure of the given event type to be able to generate a table in UI.
     *
     * @param recordings collection of paths to the JFR recording files
     * @param eventType type of the events to be fetched from the recording
     * @return event structure in JSON format.
     */
    JsonNode eventColumns(List<Path> recordings, Type eventType);

}
