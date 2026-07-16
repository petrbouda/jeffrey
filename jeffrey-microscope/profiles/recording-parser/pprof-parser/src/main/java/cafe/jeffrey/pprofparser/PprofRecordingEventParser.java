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

import com.google.perftools.profiles.ProfileProto.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
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

    @Override
    public void start(EventWriter eventWriter, Path recording) {
        Profile profile = streamReader.read(recording);
        LOG.info("Parsing pprof recording: recording={} sample_types={} samples={}",
                recording, profile.getSampleTypeCount(), profile.getSampleCount());

        SingleThreadedEventWriter writer = eventWriter.newSingleThreadedWriter();
        new PprofProfileReader(writer).read(profile);
    }
}
