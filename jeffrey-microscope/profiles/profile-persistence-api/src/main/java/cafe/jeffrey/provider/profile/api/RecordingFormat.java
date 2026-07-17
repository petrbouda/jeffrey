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
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

/**
 * Everything Jeffrey needs to know about one recording format, bundled behind a single interface.
 * A format module (pprof, JFR, ...) ships one implementation; the composition root registers it in
 * the {@link RecordingFormatRegistry} and every dispatch point (event parsing, upload-time
 * metadata, event-summary categorization, feature gating) resolves through the registry instead of
 * branching on a concrete format. Adding a new format means adding a module with one
 * {@code RecordingFormat} implementation plus a single registry entry.
 */
public interface RecordingFormat {

    /**
     * The event source stamped on recordings of this format.
     */
    RecordingEventSource eventSource();

    /**
     * The file type whose matcher recognizes this format's uploaded recording files.
     */
    SupportedRecordingFile fileType();

    /**
     * Parser converting a recording file into events on the shared event-writing pipeline.
     */
    RecordingEventParser eventParser();

    /**
     * Parser extracting upload-time metadata (size, time window, event source).
     */
    RecordingInformationParser informationParser();

    /**
     * Capability flags consumers use instead of branching on the concrete format.
     */
    RecordingFormatCapabilities capabilities();

    /**
     * @return the resolver mapping this format's event-type codes to logical flamegraph
     * categories, or {@code null} if the format has no category concept (summaries are
     * served uncategorized)
     */
    EventCategoryResolver eventCategoryResolver();
}
