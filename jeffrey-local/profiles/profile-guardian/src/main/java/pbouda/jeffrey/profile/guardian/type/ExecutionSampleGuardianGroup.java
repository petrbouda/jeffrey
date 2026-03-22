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

import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.settings.ActiveSettings;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.app.*;
import pbouda.jeffrey.profile.guardian.guard.gc.*;
import pbouda.jeffrey.profile.guardian.guard.jit.JITCompilationGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.DeoptimizationGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.SafepointOverheadGuard;
import pbouda.jeffrey.profile.guardian.guard.jvm.VMOperationOverheadGuard;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.provider.profile.repository.ProfileEventStreamRepository;

import java.util.List;

public class ExecutionSampleGuardianGroup extends AbstractGuardianGroup {

    public ExecutionSampleGuardianGroup(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventRepository,
            ActiveSettings settings,
            long minimumSamples) {

        super("Execution Sample", profileInfo, eventRepository, settings, "Minimum for Execution Samples", minimumSamples);
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(Type.EXECUTION_SAMPLE);
    }

    @Override
    public GraphParameters graphParameters() {
        return GraphParameters.builder()
                .withEventType(Type.EXECUTION_SAMPLE)
                .build();
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return List.of(
                // Application-level guards
                new LogbackOverheadGuard("Logback CPU Overhead", ResultType.SAMPLES, profileInfo, 0.04),
                new HashMapCollisionGuard(profileInfo, 0.04),
                new RegexOverheadGuard(profileInfo, 0.04),
                new ClassLoadingOverheadGuard(profileInfo, 0.05),
                new ReflectionOverheadGuard(profileInfo, 0.05),
                new SerializationOverheadGuard(profileInfo, 0.05),
                new XMLParsingOverheadGuard(profileInfo, 0.05),
                new JSONProcessingOverheadGuard(profileInfo, 0.05),
                new ExceptionOverheadGuard(profileInfo, 0.05),
                new StringConcatOverheadGuard(profileInfo, 0.05),
                new ThreadSynchronizationOverheadGuard(profileInfo, 0.05),
                new CryptoOverheadGuard(profileInfo, 0.05),
                new Log4jOverheadGuard("Log4j CPU Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new CompressOverheadGuard(profileInfo, 0.05),
                // JIT guards
                new JITCompilationGuard(profileInfo, 0.2),
                // JVM-level guards
                new DeoptimizationGuard(profileInfo, 0.05),
                new SafepointOverheadGuard(profileInfo, 0.05),
                new VMOperationOverheadGuard(profileInfo, 0.05),
                // GC guards
                new SerialGarbageCollectionGuard(profileInfo, 0.1),
                new ParallelGarbageCollectionGuard(profileInfo, 0.1),
                new G1GarbageCollectionGuard(profileInfo, 0.1),
                new ShenandoahGarbageCollectionGuard(profileInfo, 0.1),
                new ZGarbageCollectionGuard(profileInfo, 0.1),
                new ZGenerationalGarbageCollectionGuard(profileInfo, 0.1)
        );
    }
}
