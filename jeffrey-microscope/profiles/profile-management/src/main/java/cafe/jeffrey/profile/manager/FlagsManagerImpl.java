/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.provider.profile.api.JvmFlagDetail;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of the FlagsManager for JVM flags visualization.
 */
public class FlagsManagerImpl implements FlagsManager {

    // Origin order for consistent display
    private static final List<String> ORIGIN_ORDER = List.of(
            "Command line",
            "Management",
            "Ergonomic",
            "Default"
    );

    private final ProfileEventRepository eventRepository;
    private final JvmFlagDescriptionProvider descriptionProvider;

    public FlagsManagerImpl(
            ProfileEventRepository eventRepository,
            JvmFlagDescriptionProvider descriptionProvider) {
        this.eventRepository = eventRepository;
        this.descriptionProvider = descriptionProvider;
    }

    @Override
    public FlagsData getAllFlags() {
        List<JvmFlagDetail> allFlags = eventRepository.getAllFlags().stream()
                .map(flag -> flag.withDescription(descriptionProvider.getDescription(flag.name())))
                .toList();

        // Group flags by origin with ordered map
        Map<String, List<JvmFlagDetail>> flagsByOrigin = groupByOriginOrdered(allFlags);

        int totalFlags = allFlags.size();
        int changedFlags = (int) allFlags.stream()
                .filter(JvmFlagDetail::hasChanged)
                .count();

        return new FlagsData(flagsByOrigin, totalFlags, changedFlags);
    }

    /**
     * Groups flags by origin while maintaining a consistent order.
     */
    private Map<String, List<JvmFlagDetail>> groupByOriginOrdered(List<JvmFlagDetail> flags) {
        // First, group by origin
        Map<String, List<JvmFlagDetail>> grouped = flags.stream()
                .collect(Collectors.groupingBy(JvmFlagDetail::origin));

        // Create ordered map with known origins first
        Map<String, List<JvmFlagDetail>> ordered = new LinkedHashMap<>();
        for (String origin : ORIGIN_ORDER) {
            if (grouped.containsKey(origin)) {
                ordered.put(origin, grouped.get(origin));
            }
        }

        // Add any remaining origins not in the predefined order
        for (String origin : grouped.keySet()) {
            if (!ordered.containsKey(origin)) {
                ordered.put(origin, grouped.get(origin));
            }
        }

        return ordered;
    }
}
