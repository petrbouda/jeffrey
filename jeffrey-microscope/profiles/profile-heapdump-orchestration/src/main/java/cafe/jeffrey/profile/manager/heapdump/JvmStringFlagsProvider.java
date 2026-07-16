/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.heapdump.model.JvmStringFlag;
import cafe.jeffrey.provider.profile.api.JvmFlag;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;

import java.util.List;
import java.util.Map;

/**
 * Pulls string-related JVM flags out of JFR events and enriches them with
 * human-readable descriptions.
 */
public final class JvmStringFlagsProvider {

    private static final Map<String, String> FLAG_DESCRIPTIONS = Map.ofEntries(
            Map.entry("UseStringDeduplication", "Enable string deduplication during GC"),
            Map.entry("StringDeduplicationAgeThreshold", "GC cycles before string becomes dedup candidate"),
            Map.entry("UseG1GC", "Enable G1 Garbage Collector"),
            Map.entry("UseZGC", "Enable Z Garbage Collector"),
            Map.entry("UseShenandoahGC", "Enable Shenandoah Garbage Collector"),
            Map.entry("UseParallelGC", "Enable Parallel Garbage Collector"),
            Map.entry("UseSerialGC", "Enable Serial Garbage Collector"),
            Map.entry("CompactStrings", "Use compact representation for Latin-1 strings (Java 9+)"),
            Map.entry("OptimizeStringConcat", "Optimize string concatenation operations"));

    private static final String GC_FLAG_ENABLED_VALUE = "true";

    private final ProfileEventRepository eventRepository;

    public JvmStringFlagsProvider(ProfileEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<JvmStringFlag> stringFlags() {
        return eventRepository.getStringRelatedFlags().stream()
                .filter(JvmStringFlagsProvider::isEnabledOrNonGc)
                .map(flag -> new JvmStringFlag(
                        flag.name(),
                        flag.value(),
                        flag.type(),
                        flag.origin(),
                        FLAG_DESCRIPTIONS.getOrDefault(flag.name(), "")))
                .toList();
    }

    private static boolean isEnabledOrNonGc(JvmFlag flag) {
        return !GarbageCollectorType.isGcFlag(flag.name()) || GC_FLAG_ENABLED_VALUE.equals(flag.value());
    }
}
