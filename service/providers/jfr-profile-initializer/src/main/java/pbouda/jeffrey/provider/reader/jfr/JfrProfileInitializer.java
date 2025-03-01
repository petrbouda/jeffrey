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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.model.GenerateProfile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class JfrProfileInitializer implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(JfrProfileInitializer.class);

    private final EventWriter writer;

    public JfrProfileInitializer(EventWriter writer) {
        this.writer = writer;
    }

    @Override
    public ProfileInfo newProfile(String projectId, String profileName, List<Path> recordingPaths) {
        var startEndTime = JdkRecordingIterators.automaticAndCollectPartial(
                recordingPaths,
                StartEndTimeEventProcessor::new,
                new StartEndTimeCollector());

        if (startEndTime.start() == null || startEndTime.end() == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve the start and end time of the recording: path=" + recordingPaths +
                            " start_end_time=" + startEndTime);
        }

        long start = System.nanoTime();

        String profileId = UUID.randomUUID().toString();
        GenerateProfile generateProfile = new GenerateProfile(
                profileId,
                projectId,
                profileName,
                startEndTime,
                recordingPaths);

        writer.onStart(generateProfile);

        ProfileInfo profileInfo = JdkRecordingIterators.automaticAndCollect(
                recordingPaths,
                () -> new JfrEventReaderProcessor(startEndTime, writer.newSingleThreadedWriter()),
                new WriterOnCompleteCollector(writer));
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        LOG.info("Events persisted to the database: elapsed_ms={}", millis);

        return profileInfo;
    }
}
