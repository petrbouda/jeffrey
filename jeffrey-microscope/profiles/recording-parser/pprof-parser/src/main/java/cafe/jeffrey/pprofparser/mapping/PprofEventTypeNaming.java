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

package cafe.jeffrey.pprofparser.mapping;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps a pprof {@code sample_type} (a {@code type}/{@code unit} pair, e.g. {@code cpu}/{@code
 * nanoseconds} or {@code alloc_space}/{@code bytes}) onto a Jeffrey {@code pprof.<type>} event type.
 * A single pprof profile carries several value dimensions (a Go heap profile has four:
 * alloc/inuse × objects/space); each becomes its own event type so they stay independently
 * browsable, matching how {@code go tool pprof} lets you switch the sample index.
 */
public final class PprofEventTypeNaming {

    public record PprofEventType(String name, String label, List<String> categories) {
    }

    private static final String CATEGORY_PPROF = "pprof";
    private static final String CATEGORY_CPU = "CPU";
    private static final String CATEGORY_ALLOCATION = "Allocation";
    private static final String CATEGORY_BLOCKING = "Blocking";
    private static final String CATEGORY_WALL = "Wall-Clock";

    private static final String FALLBACK_TYPE = "samples";
    private static final String LABEL_SUFFIX = " (pprof)";

    private static final Map<String, String> LABEL_OVERRIDES = Map.ofEntries(
            Map.entry("cpu", "CPU"),
            Map.entry("samples", "Execution Samples"),
            Map.entry("wall", "Wall Clock"),
            Map.entry("alloc_space", "Allocated Space"),
            Map.entry("alloc_objects", "Allocated Objects"),
            Map.entry("inuse_space", "In-Use Space"),
            Map.entry("inuse_objects", "In-Use Objects"),
            Map.entry("contentions", "Lock Contentions"),
            Map.entry("delay", "Lock Delay"),
            Map.entry("goroutine", "Goroutines"),
            Map.entry("threadcreate", "Thread Creation"));

    private static final Set<Character> ALLOWED_NAME_CHARACTERS = Set.of('_', '-');

    private PprofEventTypeNaming() {
    }

    public static PprofEventType resolve(String sampleType, String sampleUnit) {
        String type = sampleType == null || sampleType.isBlank() ? FALLBACK_TYPE : sampleType;
        String lower = type.toLowerCase(Locale.ROOT);

        String name = EventTypeName.PPROF_NAMESPACE + sanitize(type);
        String label = LABEL_OVERRIDES.getOrDefault(lower, prettify(type)) + LABEL_SUFFIX;
        return new PprofEventType(name, label, categories(lower));
    }

    private static List<String> categories(String lowerType) {
        return switch (PprofEventCategory.ofDimension(lowerType)) {
            case EXECUTION -> List.of(CATEGORY_PPROF, CATEGORY_CPU);
            case WALL -> List.of(CATEGORY_PPROF, CATEGORY_WALL);
            case ALLOCATION -> List.of(CATEGORY_PPROF, CATEGORY_ALLOCATION);
            case BLOCKING -> List.of(CATEGORY_PPROF, CATEGORY_BLOCKING);
            case OTHER -> List.of(CATEGORY_PPROF);
        };
    }

    private static String sanitize(String type) {
        StringBuilder sanitized = new StringBuilder(type.length());
        for (char c : type.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || ALLOWED_NAME_CHARACTERS.contains(c)) {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    private static String prettify(String type) {
        String spaced = type.replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return type;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
