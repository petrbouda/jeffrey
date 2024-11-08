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
import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.profile.viewer.EventViewerGenerator;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.DbBasedCacheRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DbBasedViewerManager implements EventViewerManager {

    private final List<Path> recordings;
    private final DbBasedCacheRepository cacheRepository;
    private final EventViewerGenerator generator;

    public DbBasedViewerManager(
            ProfileDirs profileDirs,
            DbBasedCacheRepository cacheRepository,
            EventViewerGenerator generator) {

        this.recordings = profileDirs.allRecordingPaths();
        this.cacheRepository = cacheRepository;
        this.generator = generator;
    }

    @Override
    public JsonNode allEventTypes() {
        Optional<JsonNode> resultOpt = cacheRepository.get(CacheKey.ALL_EVENT_TYPES, JsonNode.class);
        if (resultOpt.isPresent()) {
            return resultOpt.get();
        } else {
            JsonNode allEventTypes = generator.allEventTypes(recordings);
            cacheRepository.insert(CacheKey.ALL_EVENT_TYPES, allEventTypes);
            return allEventTypes;
        }
    }

    @Override
    public JsonNode events(Type eventType) {
        return generator.events(recordings, eventType);
    }

    @Override
    public JsonNode eventColumns(Type eventType) {
        return generator.eventColumns(recordings, eventType);
    }
}
