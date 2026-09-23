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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.microscope.model.RecordingEventSource;

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
