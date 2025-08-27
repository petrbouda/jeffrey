/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.RecordingParserProvider;
import pbouda.jeffrey.provider.reader.jfr.jdk.SafeJfrRecordingInformationParser;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

public class JfrRecordingParserProvider implements RecordingParserProvider {

    private static final Path DEFAULT_TEMP_RECORDINGS_FOLDER =
            Path.of(System.getProperty("java.io.tmpdir"), "jeffrey-temp-recordings");

    private Path recordingsTempPath;
    private Clock clock;

    @Override
    public void initialize(Map<String, String> properties, Clock clock) {
        this.recordingsTempPath = Config.parsePath(
                properties, "temp-recordings.path", DEFAULT_TEMP_RECORDINGS_FOLDER);
        this.clock = clock;
    }

    @Override
    public RecordingEventParser newRecordingEventParser() {
        return new JfrRecordingEventParser(recordingsTempPath, clock);
    }

    @Override
    public RecordingInformationParser newRecordingInformationParser() {
        return new SafeJfrRecordingInformationParser();
    }
}
