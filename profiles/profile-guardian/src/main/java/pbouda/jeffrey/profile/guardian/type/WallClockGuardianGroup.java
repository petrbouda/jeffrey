/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.provider.profile.repository.ProfileEventStreamRepository;

import java.util.List;

public class WallClockGuardianGroup extends AbstractGuardianGroup {

    public WallClockGuardianGroup(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventRepository,
            ActiveSettings settings,
            long minimumSamples) {

        super("Wall Clock", profileInfo, eventRepository, settings, "Minimum for Wall Clock Samples", minimumSamples);
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(Type.WALL_CLOCK_SAMPLE);
    }

    @Override
    public GraphParameters graphParameters() {
        return GraphParameters.builder()
                .withEventType(Type.WALL_CLOCK_SAMPLE)
                .build();
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return List.of(
                new LogbackOverheadGuard("Logback Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new Log4jOverheadGuard("Log4j Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new HashMapCollisionGuard(profileInfo, 0.05),
                new RegexOverheadGuard(profileInfo, 0.05),
                new ReflectionOverheadGuard("Reflection Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new ExceptionOverheadGuard("Exception Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new CryptoOverheadGuard("Crypto/TLS Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new ClassLoadingOverheadGuard("Class Loading Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05),
                new ThreadSynchronizationOverheadGuard("Thread Synchronization Wall-Clock Overhead", ResultType.SAMPLES, profileInfo, 0.05)
        );
    }
}
