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

package cafe.jeffrey.provisioner;

import cafe.jeffrey.provisioner.feature.JvmFeature;
import cafe.jeffrey.provisioner.placeholder.Placeholders;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The features a run asked for, in the order their options are written.
 *
 * <p>Order is part of the contract: the profiler agent is first, and {@code additional-jvm-options}
 * is last so a deployment can override an option Jeffrey set for it, since a later JVM option wins
 * over an earlier one.
 */
public record JvmFeatures(List<JvmFeature> features) {

    private static final String OPTION_SEPARATOR = " ";

    /**
     * @param profiler the async-profiler agent, already resolved against the {@code profiler-path} library
     *                 since that decision needs the filesystem and logs its outcome
     */
    public static JvmFeatures of(InitConfig config, JvmFeature.AsyncProfiler profiler) {
        return new JvmFeatures(List.of(
                profiler,
                new JvmFeature.DebugNonSafepoints(config.isDebugNonSafepointsEnabled()),
                new JvmFeature.PerfCounters(config.isPerfCountersEnabled()),
                new JvmFeature.HeapDump(config.resolveHeapDumpType()),
                new JvmFeature.Heartbeat(config.isHeartbeatEnabled()),
                new JvmFeature.TracingEventThresholds(
                        config.isSpanTracingEnabled(), config.getTracingJfrEventSettings()),
                new JvmFeature.AdditionalOptions(config.getAdditionalJvmOptions())));
    }

    /** The JVM options for every feature that is switched on, space-separated. */
    public String render(Path sessionPath, Placeholders placeholders) {
        return features.stream()
                .map(feature -> feature.render(sessionPath, placeholders))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(OPTION_SEPARATOR));
    }
}
