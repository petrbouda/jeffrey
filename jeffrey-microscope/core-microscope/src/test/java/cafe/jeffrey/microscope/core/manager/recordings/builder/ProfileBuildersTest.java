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

package cafe.jeffrey.microscope.core.manager.recordings.builder;

import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProfileBuildersTest {

    private final PipelineProfileBuilder pipeline = mock(PipelineProfileBuilder.class);
    private final HeapDumpProfileBuilder heapDump = mock(HeapDumpProfileBuilder.class);

    private final ProfileBuilders builders = new ProfileBuilders(Map.of(
            ProfileKind.JFR, pipeline,
            ProfileKind.STACK_SAMPLES, pipeline,
            ProfileKind.HEAP_DUMP, heapDump));

    @Test
    void picksTheBuilderOfTheSourcesKind() {
        assertSame(pipeline, builders.of(RecordingEventSource.JDK));
        assertSame(pipeline, builders.of(RecordingEventSource.ASYNC_PROFILER));
        assertSame(pipeline, builders.of(RecordingEventSource.UNKNOWN));
        assertSame(pipeline, builders.of(RecordingEventSource.PPROF));
        assertSame(pipeline, builders.of(RecordingEventSource.OPEN_TELEMETRY));
        assertSame(heapDump, builders.of(RecordingEventSource.HEAP_DUMP));
    }

    @Test
    void refusesAKindNoBuilderIsRegisteredFor() {
        ProfileBuilders jfrOnly = new ProfileBuilders(Map.of(ProfileKind.JFR, pipeline));

        assertThrows(IllegalArgumentException.class, () -> jfrOnly.of(RecordingEventSource.HEAP_DUMP));
    }
}
