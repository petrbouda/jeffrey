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

package pbouda.jeffrey.profile.guardian.type;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.app.HashMapCollisionGuard;
import pbouda.jeffrey.profile.guardian.guard.app.LogbackOverheadGuard;
import pbouda.jeffrey.profile.guardian.guard.app.RegexOverheadGuard;
import pbouda.jeffrey.profile.guardian.guard.gc.*;
import pbouda.jeffrey.profile.guardian.guard.jit.JITCompilationGuard;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.settings.ActiveSettings;

import java.util.List;

public class ExecutionSampleGuardianGroup extends AbstractGuardianGroup {

    public ExecutionSampleGuardianGroup(ActiveSettings settings, long minimumSamples) {
        super(settings, "Minimum for Execution Samples", minimumSamples);
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(Type.EXECUTION_SAMPLE);
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return List.of(
                new LogbackOverheadGuard("Logback CPU Overhead", ResultType.SAMPLES, profileInfo, 0.04),
                new HashMapCollisionGuard(profileInfo, 0.04),
                new RegexOverheadGuard(profileInfo, 0.04),
                new JITCompilationGuard(profileInfo, 0.2),
                new SerialGarbageCollectionGuard(profileInfo, 0.1),
                new ParallelGarbageCollectionGuard(profileInfo, 0.1),
                new G1GarbageCollectionGuard(profileInfo, 0.1),
                new ShenandoahGarbageCollectionGuard(profileInfo, 0.1),
                new ZGarbageCollectionGuard(profileInfo, 0.1),
                new ZGenerationalGarbageCollectionGuard(profileInfo, 0.1)
        );
    }
}
