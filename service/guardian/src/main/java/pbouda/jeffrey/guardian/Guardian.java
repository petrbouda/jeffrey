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
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.collector.FrameCollector;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.generator.basic.info.EventInformationProvider;
import pbouda.jeffrey.guardian.guard.Guard;
import pbouda.jeffrey.guardian.guard.Guard.ProfileInfo;
import pbouda.jeffrey.guardian.guard.TotalSamplesGuard;
import pbouda.jeffrey.guardian.guard.gc.*;
import pbouda.jeffrey.guardian.guard.jit.JITCompilationGuard;
import pbouda.jeffrey.guardian.jafar.JafarExecutionSampleRecordingFileIterator;
import pbouda.jeffrey.guardian.jafar.JafarRecordingIterators;
import pbouda.jeffrey.guardian.preconditions.*;
import pbouda.jeffrey.guardian.traverse.FrameTraversal;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class Guardian {

    public List<GuardianResult> process(Config config) {
        long start = System.nanoTime();

        Frame frame = JafarRecordingIterators.automaticAndCollect(
                config.primaryRecordings(),
                JafarExecutionSampleRecordingFileIterator::new,
                FrameCollector.IDENTITY);

//        Frame frame = JdkRecordingIterators.automaticAndCollect(
//                config.primaryRecordings(),
//                EventProcessors.executionSamples(config),
//                FrameCollector.IDENTITY);

        long frameTimestamp = System.nanoTime();

        GuardRecordingInformation recordingInfo = JdkRecordingIterators.automatic(
                        config.primaryRecordings(), GuardRecordingInformationEventProcessor::new)
                .partialCollect(new PreconditionsCollector());

        long recordingInfoTimestamp = System.nanoTime();

        List<EventSummary> eventSummaries = new EventInformationProvider(config.primaryRecordings(), false)
                .get();

        long eventInfoTimestamp = System.nanoTime();

        Preconditions preconditions = new PreconditionsBuilder()
                .withEventTypes(eventSummaries)
                .withEventSource(recordingInfo.getEventSource())
                .withDebugSymbolsAvailable(recordingInfo.getDebugSymbolsAvailable())
                .withKernelSymbolsAvailable(recordingInfo.getKernelSymbolsAvailable())
                .withGarbageCollectorType(recordingInfo.getGarbageCollectorType())
                .build();


        ProfileInfo profileInfo = new ProfileInfo(config.primaryId(), Type.EXECUTION_SAMPLE);
        List<? extends Guard> candidateGuards = List.of(
                new TotalSamplesGuard(500),
                new JITCompilationGuard(profileInfo, 0.2),
                new SerialGarbageCollectionGuard(profileInfo, 0.1),
                new ParallelGarbageCollectionGuard(profileInfo, 0.1),
                new G1GarbageCollectionGuard(profileInfo, 0.1),
                new ShenandoahGarbageCollectionGuard(profileInfo, 0.1),
                new ZGarbageCollectionGuard(profileInfo, 0.1),
                new ZGenerationalGarbageCollectionGuard(profileInfo, 0.1)
        );

        List<? extends Guard> guards = candidateGuards.stream()
                .filter(guard -> guard.initialize(preconditions))
                .toList();

        FrameTraversal traversal = new FrameTraversal(frame);
        traversal.traverseWith(guards);

        long guardianTimestamp = System.nanoTime();

        List<GuardianResult> results = candidateGuards.stream()
                .map(Guard::result)
                .toList();

        System.out.println(
                "Frame: " + Duration.ofNanos(frameTimestamp - start).toMillis() +
                "\n Recording Info: " + Duration.ofNanos(recordingInfoTimestamp - frameTimestamp).toMillis() +
                "\n Event Info: " + Duration.ofNanos(eventInfoTimestamp - recordingInfoTimestamp).toMillis() +
                "\n Guardian: " + Duration.ofNanos(guardianTimestamp - eventInfoTimestamp).toMillis() +
                "\n Total: " + Duration.ofNanos(guardianTimestamp - start).toMillis()
        );

        return results;
    }

    public static void main(String[] args) {
        String homeDir = System.getProperty("user.home");
        String recordingPath = ".jeffrey/recordings/jeffrey-persons/serde/jeffrey-persons-full-direct-serde.jfr";
        Config config = new ConfigBuilder<>()
                .withPrimaryId("some-primary-id")
                .withPrimaryRecording(Path.of(homeDir, recordingPath))
                .build();

        Guardian guardian = new Guardian();
        guardian.process(config);
    }
}
