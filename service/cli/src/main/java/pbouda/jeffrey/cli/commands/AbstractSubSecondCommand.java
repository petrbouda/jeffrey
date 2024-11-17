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

package pbouda.jeffrey.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.EventTypeName;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.StartEndTimeCollector;
import pbouda.jeffrey.generator.basic.StartEndTimeEventProcessor;
import pbouda.jeffrey.generator.subsecond.SubSecondConfig;
import pbouda.jeffrey.generator.subsecond.SubSecondConfigBuilder;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public abstract class AbstractSubSecondCommand implements Runnable {

    @Option(
            names = {"-e", "--event-type"},
            defaultValue = EventTypeName.EXECUTION_SAMPLE,
            description = "Selects events for generating a graph (e.g. jdk.ExecutionSample)")
    String eventType = Type.EXECUTION_SAMPLE.code();

    @Option(
            names = {"-o", "--output"},
            description = "Path to the file with the generated graph (default is the current folder with a filename '<jfr-name>.html')")
    File outputFile;

    @Option(
            names = {"-w", "--weight"},
            description = "Uses event's weight instead of # of samples (currently supported: jdk.ObjectAllocationSample, jdk.ObjectAllocationInNewTLAB, jdk.ObjectAllocationOutsideTLAB, jdk.ThreadPark, jdk.JavaMonitorWait, jdk.JavaMonitorEnter)")
    boolean weight = false;

    protected final JsonNode generateData(Path recording) {
        ProfilingStartEnd startEndTime = JdkRecordingIterators.automaticAndCollectPartial(
                List.of(recording),
                StartEndTimeEventProcessor::new,
                new StartEndTimeCollector());

        if (startEndTime.isInvalid()) {
            System.out.println("The recording does not contain a mandatory event: jdk.ActiveRecording");
            System.exit(1);
        }

        SubSecondConfigBuilder configBuilder = SubSecondConfig.builder()
                .withEventType(Type.fromCode(eventType))
                .withCollectWeight(weight)
                .withDuration(Duration.ofMinutes(5))
                .withProfilingStart(startEndTime.start())
                .withGeneratingStart(Duration.ZERO);

        if (Files.isDirectory(recording)) {
            configBuilder.withRecordingDir(recording);
        } else {
            configBuilder.withRecording(recording);
        }

        return new SubSecondGeneratorImpl().generate(configBuilder.build());
    }
}
