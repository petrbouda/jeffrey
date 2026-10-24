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

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.storage.recording.api.file.Recording;

import java.util.Optional;

/**
 * How one kind of recording becomes its profile. Each initializable kind builds its own way — a JFR
 * recording is parsed into events, a heap dump is indexed beside its copy — and decides for itself
 * whether a profile it built before can be reused.
 */
public sealed interface ProfileBuilder
        permits PipelineProfileBuilder, HeapDumpProfileBuilder, PerfCountersProfileBuilder {

    /**
     * An explicit Analyze on a recording that already has a profile.
     *
     * @return the profile to report — joined, resumed or simply kept — or empty when the old one was
     *         dropped and a fresh one has to be built
     */
    Optional<String> reuse(Recording recording);

    /**
     * Builds a fresh profile for the recording.
     *
     * @return the new profile's id
     */
    String build(Recording recording);

    /** How far the profile's initialization has got, as the recordings list shows it. */
    PipelineProgress progress(String profileId);
}
