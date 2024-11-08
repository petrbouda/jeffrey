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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.profile.viewer.EventViewerProvider;

public class EventViewerManagerImpl implements EventViewerManager {

    private final EventViewerProvider eventViewerProvider;

    public EventViewerManagerImpl(EventViewerProvider eventViewerProvider) {
        this.eventViewerProvider = eventViewerProvider;
    }

    @Override
    public JsonNode allEventTypes() {
        return eventViewerProvider.allEventTypes();
    }

    @Override
    public JsonNode events(Type eventType) {
        return eventViewerProvider.events(eventType);
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        return eventViewerProvider.eventColumns(eventType);
    }
}
