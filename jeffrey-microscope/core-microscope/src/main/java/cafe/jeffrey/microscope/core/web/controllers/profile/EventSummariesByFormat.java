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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.EventCategoryResolver;
import cafe.jeffrey.provider.profile.api.RecordingFormat;

import java.util.List;

/**
 * Serves the flamegraph {@code /events} list according to the recording format's capabilities:
 * formats with a curated event list (JFR) return their well-known event summaries unchanged, while
 * formats with open-ended dimension names (pprof) return all recorded event types, each stamped
 * with the logical category from the format's {@link EventCategoryResolver}. Controllers stay
 * format-blind — a new format only describes itself in its {@code RecordingFormat}.
 */
final class EventSummariesByFormat {

    private EventSummariesByFormat() {
    }

    static List<EventSummaryResult> list(RecordingFormat format, FlamegraphManager flamegraphManager) {
        if (format.capabilities().curatedEventSummaries()) {
            return flamegraphManager.eventSummaries();
        }
        List<EventSummaryResult> summaries = flamegraphManager.allEventSummaries();
        EventCategoryResolver categoryResolver = format.eventCategoryResolver();
        if (categoryResolver == null) {
            return summaries;
        }
        return summaries.stream()
                .map(summary -> summary.withCategory(categoryResolver.resolve(summary.code())))
                .toList();
    }
}
