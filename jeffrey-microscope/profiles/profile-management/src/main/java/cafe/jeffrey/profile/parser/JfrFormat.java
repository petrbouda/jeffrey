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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.EventCategoryResolver;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingFormat;
import cafe.jeffrey.provider.profile.api.RecordingFormatCapabilities;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

/**
 * The JFR recording format ({@code .jfr} and its LZ4-compressed variant). This is the registry's
 * default format: it also handles recordings whose event source is resolved later from the
 * recorded event types (JDK vs async-profiler), so it covers every source without a dedicated
 * {@link RecordingFormat} registration.
 */
public final class JfrFormat implements RecordingFormat {

    private static final RecordingFormatCapabilities CAPABILITIES = new RecordingFormatCapabilities(
            /* timestampedEvents */ true,
            /* curatedEventSummaries */ true);

    private final RecordingEventParser eventParser;
    private final RecordingInformationParser informationParser;

    public JfrFormat(TempDirFactory tempDirFactory) {
        this.eventParser = new JfrRecordingEventParser(tempDirFactory, new Lz4Compressor(tempDirFactory));
        this.informationParser = new JfrRecordingInformationParser(tempDirFactory);
    }

    @Override
    public RecordingEventSource eventSource() {
        return RecordingEventSource.JDK;
    }

    @Override
    public SupportedRecordingFile fileType() {
        return SupportedRecordingFile.JFR;
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
        return null;
    }
}
