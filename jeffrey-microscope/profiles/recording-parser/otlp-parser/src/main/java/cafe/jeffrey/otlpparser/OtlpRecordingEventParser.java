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

package cafe.jeffrey.otlpparser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
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

    /**
     * Each file gets its own writer, the way a JFR chunk does: the writers are independent and the
     * events carry their own timestamps, so nothing depends on the files being read together.
     */
    @Override
    public void start(EventWriter eventWriter, RecordingSources sources) {
        for (Path recording : sources.files()) {
            OtlpProfileReader reader = new OtlpProfileReader(eventWriter.newSingleThreadedWriter());
            Duration elapsed = Measuring.r(() -> reader.read(recording));
            LOG.info("OTLP recording parsed: recording={} duration_in_ms={}", recording, elapsed.toMillis());
        }
    }
}
