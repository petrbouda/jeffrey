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

package cafe.jeffrey.microscope.core.profile.ingest;

import cafe.jeffrey.microscope.core.profile.parser.otlp.OtlpRecordingInformationParser;
import cafe.jeffrey.microscope.core.profile.parser.pprof.PprofRecordingInformationParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingInformation;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingInformationParser;
import cafe.jeffrey.microscope.core.profile.persistence.api.RecordingSources;
import cafe.jeffrey.microscope.model.recording.ManagedFile;

/**
 * Selects the {@link RecordingInformationParser} for an uploaded recording by its file type: pprof
 * files ({@code .pprof} / {@code .pb.gz}) go to the pprof parser, OpenTelemetry profiles ({@code .otlp})
 * to the OTLP parser, everything else (JFR and its LZ4 variant) to the JFR parser. Upload-time metadata
 * (event source + time window) is otherwise read uniformly regardless of the recording format.
 */
public class FileTypeDispatchingRecordingInformationParser implements RecordingInformationParser {

    private final RecordingInformationParser jfrParser;
    private final RecordingInformationParser pprofParser;
    private final RecordingInformationParser otlpParser;

    public FileTypeDispatchingRecordingInformationParser(
            RecordingInformationParser jfrParser,
            RecordingInformationParser pprofParser,
            RecordingInformationParser otlpParser) {

        this.jfrParser = jfrParser;
        this.pprofParser = pprofParser;
        this.otlpParser = otlpParser;
    }

    public FileTypeDispatchingRecordingInformationParser(RecordingInformationParser jfrParser) {
        this(jfrParser, new PprofRecordingInformationParser(), new OtlpRecordingInformationParser());
    }

    /**
     * Dispatches on the first file's type. A recording is one format throughout — the files of a
     * session are all chunks of the same profiler run — so there is nothing to decide per file.
     */
    @Override
    public RecordingInformation provide(RecordingSources sources) {
        ManagedFile fileType = ManagedFile.of(sources.first());
        if (fileType == ManagedFile.PPROF) {
            return pprofParser.provide(sources);
        }
        if (fileType == ManagedFile.OTLP_PROFILE) {
            return otlpParser.provide(sources);
        }
        return jfrParser.provide(sources);
    }
}
