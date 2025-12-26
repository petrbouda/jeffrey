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

import pbouda.jeffrey.common.model.EventSummary;
import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.settings.ActiveSettings;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.RecordsFrameIterator;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.TotalSamplesGuard;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.FrameTraversal;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractGuardianGroup implements GuardianGroup {

    private final String profileId;
    private final ProfileEventStreamRepository eventRepository;
    private final ActiveSettings settings;
    private final String totalSamplesGuardName;
    private final long minimumSamples;

    public AbstractGuardianGroup(
            ProfileInfo profileInfo,
            ProfileEventStreamRepository eventRepository,
            ActiveSettings settings,
            String totalSamplesGuardName,
            long minimumSamples) {

        this.profileId = profileInfo.id();
        this.eventRepository = eventRepository;
        this.settings = settings;
        this.totalSamplesGuardName = totalSamplesGuardName;
        this.minimumSamples = minimumSamples;
    }

    abstract List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo);

    @Override
    public List<GuardianResult> execute(EventSummary eventSummary, Preconditions preconditions) {
        GraphParameters params = graphParameters();

        Guard.ProfileInfo profileInfo = new Guard.ProfileInfo(profileId, params.eventType());
        List<? extends Guard> candidateGuards = candidateGuards(profileInfo);

        if (eventSummary.samples() >= minimumSamples) {
            List<? extends Guard> guards = candidateGuards.stream()
                    .filter(guard -> guard.initialize(preconditions))
                    .toList();

            Frame frame = new RecordsFrameIterator(params, eventRepository)
                    .iterate();

            FrameTraversal traversal = new FrameTraversal(frame);
            traversal.traverseWith(guards);
        }

        List<GuardianResult> results = new ArrayList<>();
        results.add(new TotalSamplesGuard(totalSamplesGuardName, eventSummary.samples(), minimumSamples).result());

        candidateGuards.stream()
                .map(Guard::result)
                .forEach(results::add);

        return results;
    }
}
