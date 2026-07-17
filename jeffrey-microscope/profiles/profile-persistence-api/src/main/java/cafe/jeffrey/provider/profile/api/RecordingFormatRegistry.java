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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single lookup table for {@link RecordingFormat}s. Built once at the composition root from
 * the list of available formats plus a default (JFR); every dispatch point resolves a format by
 * the recording's {@link RecordingEventSource} or by its file type and falls back to the default,
 * so no dispatch code anywhere branches on a concrete format.
 */
public final class RecordingFormatRegistry {

    private final Map<RecordingEventSource, RecordingFormat> bySource;
    private final Map<SupportedRecordingFile, RecordingFormat> byFileType;
    private final RecordingFormat defaultFormat;

    private RecordingFormatRegistry(
            Map<RecordingEventSource, RecordingFormat> bySource,
            Map<SupportedRecordingFile, RecordingFormat> byFileType,
            RecordingFormat defaultFormat) {

        this.bySource = bySource;
        this.byFileType = byFileType;
        this.defaultFormat = defaultFormat;
    }

    /**
     * @param formats       formats with a dedicated registration; the default format does not need
     *                      to be repeated here
     * @param defaultFormat format used when no registered format matches (including {@code null}
     *                      sources and unrecognized files)
     */
    public static RecordingFormatRegistry of(List<RecordingFormat> formats, RecordingFormat defaultFormat) {
        Map<RecordingEventSource, RecordingFormat> bySource = new HashMap<>();
        Map<SupportedRecordingFile, RecordingFormat> byFileType = new HashMap<>();
        for (RecordingFormat format : formats) {
            RecordingFormat previousSource = bySource.put(format.eventSource(), format);
            RecordingFormat previousFile = byFileType.put(format.fileType(), format);
            if (previousSource != null || previousFile != null) {
                throw new IllegalArgumentException(
                        "Duplicate recording format registration: event_source=" + format.eventSource()
                                + " file_type=" + format.fileType());
            }
        }
        return new RecordingFormatRegistry(bySource, byFileType, defaultFormat);
    }

    public RecordingFormat bySource(RecordingEventSource eventSource) {
        return bySource.getOrDefault(eventSource, defaultFormat);
    }

    public RecordingFormat byFile(Path recordingPath) {
        return byFileType.getOrDefault(SupportedRecordingFile.of(recordingPath), defaultFormat);
    }

    /**
     * A {@link RecordingInformationParser} view of this registry: each recording's metadata is
     * parsed by the format matching its file type.
     */
    public RecordingInformationParser informationParser() {
        return recordingPath -> byFile(recordingPath).informationParser().provide(recordingPath);
    }
}
