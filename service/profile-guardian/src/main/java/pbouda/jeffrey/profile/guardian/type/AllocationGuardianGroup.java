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
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.app.LogbackOverheadGuard;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;

import java.util.List;

public class AllocationGuardianGroup extends AbstractGuardianGroup {

    public AllocationGuardianGroup(
            ProfileEventRepository eventRepository,
            ActiveSettings settings,
            long minimumSamples) {

        super(eventRepository, settings, "Minimum for Allocation Samples", minimumSamples);
    }

    @Override
    public List<Type> applicableTypes() {
        return List.of(
                Type.OBJECT_ALLOCATION_SAMPLE,
                Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
                Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
    }

    @Override
    List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo) {
        return List.of(
                new LogbackOverheadGuard("Logback Allocation Overhead", ResultType.WEIGHT, profileInfo, 0.1)
        );
    }
}
