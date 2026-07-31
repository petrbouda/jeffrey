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

package cafe.jeffrey.performance.analyst.flamegraph;

import cafe.jeffrey.shared.common.model.Type;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The profiles Jeffrey builds an AI prompt for, plus the UI label and cache slug for each. Single source
 * of truth shared by the exporter (which builds prompts) and the cache manager (which maps a stored row
 * back to its event type and label).
 *
 * <p>Each entry folds one or more JFR event types into a single profile, because that is how a person
 * thinks about them: "allocation" is one question even though the JVM answers it through three separate
 * events depending on TLAB behaviour and JDK version.</p>
 */
public enum AiPromptType {

    CPU("CPU", "execution-sample", Type.EXECUTION_SAMPLE),

    WALL_CLOCK("Wall-Clock", "wall-clock-sample", Type.WALL_CLOCK_SAMPLE),

    ALLOCATION("Allocation", "allocation-sample",
            Type.OBJECT_ALLOCATION_SAMPLE,
            Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
            Type.OBJECT_ALLOCATION_OUTSIDE_TLAB),

    BLOCKING("Blocking", "blocking-sample",
            Type.JAVA_MONITOR_ENTER,
            Type.JAVA_MONITOR_WAIT,
            Type.THREAD_PARK);

    private static final String FILE_EXTENSION = ".md";

    private final String label;
    private final String slug;
    private final List<Type> eventTypes;

    AiPromptType(String label, String slug, Type... eventTypes) {
        this.label = label;
        this.slug = slug;
        this.eventTypes = List.of(eventTypes);
    }

    /**
     * The event types folded into this profile, in preference order — the first one present in a
     * recording is the one whose tree is rendered.
     */
    public List<Type> eventTypes() {
        return eventTypes;
    }

    /**
     * The event type this profile is identified by in the database and the UI. Always the primary
     * (first) type, so a stored row keeps a stable identity even if a recording happens to carry a
     * different member of the group.
     */
    public Type primaryEventType() {
        return eventTypes.getFirst();
    }

    public String label() {
        return label;
    }

    public String fileName() {
        return slug + FILE_EXTENSION;
    }

    /**
     * Every JFR event type any prompt is built from — what the parser is restricted to.
     */
    public static Set<Type> allEventTypes() {
        return Arrays.stream(values())
                .flatMap(promptType -> promptType.eventTypes.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Optional<AiPromptType> byEventCode(String eventCode) {
        return Arrays.stream(values())
                .filter(promptType -> promptType.eventTypes.stream()
                        .anyMatch(type -> type.code().equals(eventCode)))
                .findFirst();
    }

    public static Optional<AiPromptType> byFileName(String fileName) {
        return Arrays.stream(values()).filter(t -> t.fileName().equals(fileName)).findFirst();
    }
}
