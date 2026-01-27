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
import pbouda.jeffrey.profile.guardian.guard.blocking.*;
import pbouda.jeffrey.provider.profile.repository.ProfileEventStreamRepository;

import java.util.List;

public class BlockingGuardianGroup extends AbstractGuardianGroup {

    public BlockingGuardianGroup(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventRepository,
            ActiveSettings settings,
            long minimumSamples) {

        super("Blocking", profileInfo, eventRepository, settings, "Minimum for Blocking Samples", minimumSamples);
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(
                Type.JAVA_MONITOR_ENTER,
                Type.THREAD_PARK,
                Type.THREAD_SLEEP);
    }

    @Override
    public GraphParameters graphParameters() {
        return GraphParameters.builder()
                .withEventType(Type.JAVA_MONITOR_ENTER)
                .withUseWeight(true)
                .build();
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return List.of(
                new DatabaseConnectionPoolBlockingGuard(profileInfo, 0.05),
                new LockContentionBlockingGuard(profileInfo, 0.05),
                new IOBlockingGuard(profileInfo, 0.05),
                new HttpClientBlockingGuard(profileInfo, 0.05),
                new LogbackBlockingGuard(profileInfo, 0.05),
                new Log4jBlockingGuard(profileInfo, 0.05)
        );
    }
}
