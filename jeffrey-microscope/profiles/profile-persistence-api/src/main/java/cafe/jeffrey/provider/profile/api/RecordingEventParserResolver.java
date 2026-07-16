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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.util.Map;

/**
 * Resolves the {@link RecordingEventParser} for the event source of the recording being analyzed —
 * JFR-based sources (JDK, async-profiler) and pprof profiles use different parsers over the same
 * event-writing pipeline.
 */
@FunctionalInterface
public interface RecordingEventParserResolver {

    RecordingEventParser resolve(RecordingEventSource eventSource);

    /**
     * @param parsersBySource parsers keyed by event source
     * @param defaultParser   parser used for sources without a dedicated entry (including {@code null})
     */
    static RecordingEventParserResolver of(
            Map<RecordingEventSource, RecordingEventParser> parsersBySource,
            RecordingEventParser defaultParser) {

        return eventSource -> parsersBySource.getOrDefault(eventSource, defaultParser);
    }
}
