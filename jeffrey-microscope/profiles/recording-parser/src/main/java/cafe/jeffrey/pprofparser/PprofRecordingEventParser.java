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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;

import java.nio.file.Path;

/**
 * Ingests a pprof recording ({@code .pprof} / {@code .pb.gz}, gzip-compressed protobuf) into a
 * profile database. A pprof file is a single {@code perftools.profiles.Profile} message, so — unlike
 * the chunked, parallel JFR path — this reads the whole profile on one writer thread.
 */
public class PprofRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(PprofRecordingEventParser.class);

    private final PprofStreamReader streamReader;

    public PprofRecordingEventParser() {
        this.streamReader = new PprofStreamReader();
    }

    /**
     * Each file gets its own writer, the way a JFR chunk does: the writers are independent and the
     * events carry their own timestamps, so nothing depends on the files being read together.
     */
    @Override
    public void start(EventWriter eventWriter, RecordingSources sources) {
        for (Path recording : sources.files()) {
            Profile profile = streamReader.read(recording);
            LOG.info("Parsing pprof recording: recording={} sample_types={} samples={}",
                    recording, profile.getSampleTypeCount(), profile.getSampleCount());

            SingleThreadedEventWriter writer = eventWriter.newSingleThreadedWriter();
            new PprofProfileReader(writer).read(profile);
        }
    }
}
