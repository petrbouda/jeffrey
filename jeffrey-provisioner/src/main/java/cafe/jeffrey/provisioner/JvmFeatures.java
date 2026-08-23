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
 * <p>Order is part of the contract: {@code additional-jvm-options} is last so a deployment can
 * override an option Jeffrey set for it, since a later JVM option wins over an earlier one.
 */
public record JvmFeatures(List<JvmFeature> features) {

    private static final String OPTION_SEPARATOR = " ";

    public static JvmFeatures of(InitConfig config, AppIdentity identity) {
        return new JvmFeatures(List.of(
                new JvmFeature.DebugNonSafepoints(config.isDebugNonSafepointsEnabled()),
                new JvmFeature.PerfCounters(config.isPerfCountersEnabled()),
                new JvmFeature.HeapDump(config.resolveHeapDumpType()),
                new JvmFeature.JvmLogging(config.getJvmLoggingCommand()),
                new JvmFeature.Agent(config.getAgentPath(), config.isMethodTracingEnabled(), identity),
                new JvmFeature.TracingEventThresholds(
                        config.isMethodTracingEnabled(), config.getTracingJfrEventSettings()),
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
