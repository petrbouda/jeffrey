/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.memory;

import tools.jackson.databind.JsonNode;
import cafe.jeffrey.profile.manager.model.leak.LeakCandidate;
import cafe.jeffrey.profile.manager.model.leak.LeakOverview;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;

import java.util.Comparator;
import java.util.List;

public class LeakCandidatesManagerImpl implements LeakCandidatesManager {

    private static final String OBJECT_FIELD = "object";
    private static final String OBJECT_SIZE_FIELD = "objectSize";
    private static final String OBJECT_AGE_FIELD = "objectAge";
    private static final String ARRAY_ELEMENTS_FIELD = "arrayElements";
    private static final String LAST_KNOWN_HEAP_USAGE_FIELD = "lastKnownHeapUsage";

    private final ProfileEventRepository eventRepository;

    public LeakCandidatesManagerImpl(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public LeakOverview overview() {
        List<LeakCandidate> candidates = candidates();
        long largest = candidates.stream().mapToLong(LeakCandidate::objectSizeBytes).max().orElse(0);
        long total = candidates.stream().mapToLong(LeakCandidate::objectSizeBytes).sum();
        long oldest = candidates.stream().mapToLong(LeakCandidate::objectAgeNanos).max().orElse(0);
        return new LeakOverview(candidates.size(), largest, total, oldest);
    }

    @Override
    public List<LeakCandidate> candidates() {
        return eventRepository.eventsByTypeWithFields(Type.OLD_OBJECT_SAMPLE).stream()
                .map(LeakCandidatesManagerImpl::toCandidate)
                .sorted(Comparator.comparingLong(LeakCandidate::objectSizeBytes).reversed())
                .toList();
    }

    private static LeakCandidate toCandidate(JsonNode fields) {
        return new LeakCandidate(
                Json.readString(fields, OBJECT_FIELD),
                Math.max(0, Json.readLong(fields, OBJECT_SIZE_FIELD)),
                Math.max(0, Json.readLong(fields, OBJECT_AGE_FIELD)),
                Math.max(0, Json.readInt(fields, ARRAY_ELEMENTS_FIELD)),
                Math.max(0, Json.readLong(fields, LAST_KNOWN_HEAP_USAGE_FIELD)));
    }
}
