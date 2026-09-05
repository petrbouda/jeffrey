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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.shared.common.model.Type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The machine-level sections of one profile, and whether the recording can answer each of them.
 * <p>
 * Availability is decided once, against the event types the recording actually holds, and the same
 * answer serves both purposes: {@code jvm_sections} advertises it without rendering anything, and a
 * section asked for anyway is refused with a sentence naming the events that are missing. Both matter
 * for the same reason — a dashboard rendered from a recording that captured none of its events is a
 * page of zeroes, which reads like a finding rather than like an absence.
 */
public class JvmSections {

    private final Map<String, JvmSection> sections;
    private final Supplier<Set<String>> recordedEventTypes;

    public JvmSections(ProfileManager profileManager, List<JvmSection> sections) {
        this.sections = index(sections);

        this.recordedEventTypes = memoize(() -> profileManager.flamegraphManager().allEventSummaries()
                .stream()
                .map(EventSummaryResult::code)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Every section with the events it needs and whether this recording carries them, in the order a
     * reader would work through them.
     */
    public List<SectionAvailability> availability() {
        return sections.values().stream()
                .map(section -> new SectionAvailability(
                        section.id(),
                        section.title(),
                        isAvailable(section),
                        section.eventTypes().stream().map(Type::code).sorted().toList()))
                .toList();
    }

    /**
     * The section registered under that id, or null when no section goes by it — which is a wiring
     * mistake rather than something about this profile. Whether the profile can <em>answer</em> the
     * section is a separate question, and {@link #isAvailable} is the one that asks it.
     */
    public JvmSection get(String id) {
        return sections.get(id);
    }

    /**
     * Whether the recording carries any of the events the section is built from. A section that
     * declares no event types does not depend on the recording's contents and is always available.
     */
    public boolean isAvailable(JvmSection section) {
        if (section.eventTypes().isEmpty()) {
            return true;
        }
        Set<String> recorded = recordedEventTypes.get();
        return section.eventTypes().stream().anyMatch(type -> recorded.contains(type.code()));
    }

    private static Map<String, JvmSection> index(List<JvmSection> sections) {
        // LinkedHashMap rather than Map.copyOf: jvm_sections reports them in the order a reader
        // works through them, and an immutable map would scramble that.
        Map<String, JvmSection> indexed = new LinkedHashMap<>();
        for (JvmSection section : sections) {
            indexed.put(section.id(), section);
        }
        return Collections.unmodifiableMap(indexed);
    }

    /**
     * The event-type set is read from the database and every tool call in a session asks the same
     * question of it, so it is read once per profile-scoped instance.
     */
    private static <T> Supplier<T> memoize(Supplier<T> delegate) {
        return new Supplier<>() {
            private T value;

            @Override
            public T get() {
                if (value == null) {
                    value = delegate.get();
                }
                return value;
            }
        };
    }

    /**
     * @param available  whether the recording carries any of {@code eventTypes}
     * @param eventTypes the events this section is built from, so a reader can tell the profiler what
     *                   to capture next time
     */
    public record SectionAvailability(
            String id,
            String title,
            boolean available,
            List<String> eventTypes) {
    }
}
