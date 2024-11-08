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
import pbouda.jeffrey.common.persistence.CacheKey;
import pbouda.jeffrey.common.persistence.CacheRepository;

import java.util.Optional;

public class CachingEventViewerProvider implements EventViewerProvider {

    private final EventViewerProvider eventViewerProvider;
    private final CacheRepository cacheRepository;

    public CachingEventViewerProvider(
            EventViewerProvider eventViewerProvider,
            CacheRepository cacheRepository) {

        this.eventViewerProvider = eventViewerProvider;
        this.cacheRepository = cacheRepository;
    }

    @Override
    public JsonNode allEventTypes() {
        Optional<JsonNode> cached =
                cacheRepository.get(CacheKey.PROFILE_VIEWER, JsonNode.class);

        if (cached.isPresent()) {
            return cached.get();
        } else {
            JsonNode allEventTypes = eventViewerProvider.allEventTypes();
            cacheRepository.insert(CacheKey.PROFILE_VIEWER, allEventTypes);
            return allEventTypes;
        }
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
