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

package cafe.jeffrey.otlpparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.nio.file.Path;
import java.time.Duration;

/**
 * {@link RecordingEventParser} for OpenTelemetry profiles recordings ({@code .otlp} files, see
 * {@link OtlpFileFormat}). Parsing is single-threaded: every frame carries its own dictionary, and
 * OTLP recordings are typically much smaller than JFR ones, so chunk-parallelism is not worth the
 * coordination overhead yet.
 */
public class OtlpRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(OtlpRecordingEventParser.class);

    @Override
    public void start(EventWriter eventWriter, Path recording) {
        OtlpProfileReader reader = new OtlpProfileReader(eventWriter.newSingleThreadedWriter());
        Duration elapsed = Measuring.r(() -> reader.read(recording));
        LOG.info("OTLP recording parsed: recording={} duration_in_ms={}", recording, elapsed.toMillis());
    }
}
