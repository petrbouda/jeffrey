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

package cafe.jeffrey.profile.guardian.type;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.settings.ActiveSettings;
import cafe.jeffrey.profile.guardian.definition.GuardDefinitions;
import cafe.jeffrey.profile.guardian.guard.GroupKind;
import cafe.jeffrey.profile.guardian.guard.Guard;
import cafe.jeffrey.profile.guardian.guard.GuardFactory;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;

import java.util.List;

/**
 * Guardian group for the JDK 25 CPU-time profiler ({@code jdk.CPUTimeSample}). Runs the same
 * CPU-overhead guards as {@link ExecutionSampleGuardianGroup} — CPU-time samples measure on-CPU
 * time, so the identical heuristics apply.
 */
public class CpuTimeSampleGuardianGroup extends AbstractGuardianGroup {

    private final GuardDefinitions definitions;

    public CpuTimeSampleGuardianGroup(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventRepository,
            ActiveSettings settings,
            GuardDefinitions definitions) {

        super("CPU Time Sample", profileInfo, eventRepository, settings,
                "Minimum for CPU Time Samples", definitions.minSamples(GroupKind.CPU_TIME_SAMPLE));
        this.definitions = definitions;
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(Type.CPU_TIME_SAMPLE);
    }

    @Override
    public GraphParameters graphParameters() {
        return GraphParameters.builder()
                .withEventType(Type.CPU_TIME_SAMPLE)
                .build();
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return GuardFactory.instantiateFor(GroupKind.CPU_TIME_SAMPLE, profileInfo, definitions);
    }
}
