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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sample event types we build an AI flamegraph prompt for, plus their UI label and the filesystem
 * cache slug. Single source of truth shared by the exporter (which builds prompts) and the cache
 * manager (which maps a cached {@code <slug>.md} file back to its event type and label).
 */
public enum AiPromptType {

    CPU(Type.EXECUTION_SAMPLE, "CPU", "execution-sample"),
    WALL_CLOCK(Type.WALL_CLOCK_SAMPLE, "Wall-Clock", "wall-clock-sample");

    private static final String FILE_EXTENSION = ".md";

    private final Type eventType;
    private final String label;
    private final String slug;

    AiPromptType(Type eventType, String label, String slug) {
        this.eventType = eventType;
        this.label = label;
        this.slug = slug;
    }

    public Type eventType() {
        return eventType;
    }

    public String label() {
        return label;
    }

    public String fileName() {
        return slug + FILE_EXTENSION;
    }

    public static Set<Type> eventTypes() {
        return Arrays.stream(values()).map(AiPromptType::eventType).collect(Collectors.toSet());
    }

    public static Optional<AiPromptType> byEventCode(String eventCode) {
        return Arrays.stream(values()).filter(t -> t.eventType.code().equals(eventCode)).findFirst();
    }

    public static Optional<AiPromptType> byFileName(String fileName) {
        return Arrays.stream(values()).filter(t -> t.fileName().equals(fileName)).findFirst();
    }
}
