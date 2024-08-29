/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.guardian;

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.ConfigBuilder;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.collector.FrameCollector;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.guardian.guard.Guard;
import pbouda.jeffrey.guardian.guard.TotalSamplesGuard;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class Guardian {

    public List<GuardianResult> process(Config config) {
        Frame frame = RecordingIterators.automaticAndCollect(
                config.primaryRecordings(),
                EventProcessors.executionSamples(config),
                new FrameCollector<>(Function.identity()));

        List<Guard> guards = List.of(new TotalSamplesGuard(500));

        FrameTraversal traversal = new FrameTraversal(frame);
        traversal.traverseWith(guards);

        return guards.stream()
                .map(Guard::result)
                .toList();
    }

    public static void main(String[] args) {
        String homeDir = System.getProperty("user.home");
        String recordingPath = ".jeffrey/recordings/jeffrey-persons/serde/jeffrey-persons-full-direct-serde.jfr";
        Config config = new ConfigBuilder<>()
                .withPrimaryRecording(Path.of(homeDir, recordingPath))
                .build();

        Guardian guardian = new Guardian();
        guardian.process(config);
    }
}
