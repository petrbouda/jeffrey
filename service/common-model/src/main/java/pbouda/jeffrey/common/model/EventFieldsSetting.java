/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.common.model;

public enum EventFields {
    /**
     * No fields are included and parsed for whatever event. It saves the most space. However, it disables
     * the features based on the event-fields information: e.g. Event-Viewer, Threads, Configuration, etc.
     */
    NONE,

    /**
     * Event fields are parsed only for specified events. In general, it should disable only Event-Viewer and
     * keep functionality for Threads, Configuration, etc.
     */
    MANDATORY_ONLY,

    /**
     * Event fields are parsed for all events. It enables all features based on the event-fields information.
     */
    ALL,
}
