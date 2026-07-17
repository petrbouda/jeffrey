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

package cafe.jeffrey.pprofparser;

import cafe.jeffrey.pprofparser.mapping.PprofEventCategory;
import cafe.jeffrey.provider.profile.api.EventCategoryResolver;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingFormat;
import cafe.jeffrey.provider.profile.api.RecordingFormatCapabilities;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

/**
 * The pprof recording format: everything format-specific the rest of Jeffrey needs, in one place.
 * pprof profiles are aggregated (every event carries the profile-wide timestamp, so time-resolved
 * views are meaningless) and their sample dimensions are open-ended (so all recorded event types
 * are exposed instead of a curated list, each tagged with its {@link PprofEventCategory}).
 */
public final class PprofFormat implements RecordingFormat {

    private static final RecordingFormatCapabilities CAPABILITIES = new RecordingFormatCapabilities(
            /* timestampedEvents */ false,
            /* curatedEventSummaries */ false);

    private final RecordingEventParser eventParser = new PprofRecordingEventParser();
    private final RecordingInformationParser informationParser = new PprofRecordingInformationParser();

    @Override
    public RecordingEventSource eventSource() {
        return RecordingEventSource.PPROF;
    }

    @Override
    public SupportedRecordingFile fileType() {
        return SupportedRecordingFile.PPROF;
    }

    @Override
    public RecordingEventParser eventParser() {
        return eventParser;
    }

    @Override
    public RecordingInformationParser informationParser() {
        return informationParser;
    }

    @Override
    public RecordingFormatCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public EventCategoryResolver eventCategoryResolver() {
        return eventTypeCode -> PprofEventCategory.resolve(eventTypeCode).name();
    }
}
