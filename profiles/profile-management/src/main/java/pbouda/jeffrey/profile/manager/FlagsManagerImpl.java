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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.provider.profile.model.JvmFlagDetail;
import pbouda.jeffrey.provider.profile.repository.ProfileEventRepository;

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
