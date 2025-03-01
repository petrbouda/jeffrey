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

import pbouda.jeffrey.common.EventSummary;
import pbouda.jeffrey.common.config.Config;
import pbouda.jeffrey.common.model.ActiveSettings;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.collector.FrameCollector;
import pbouda.jeffrey.frameir.processor.EventProcessors;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.guard.Guard;
import pbouda.jeffrey.profile.guardian.guard.TotalSamplesGuard;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.FrameTraversal;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractGuardianGroup implements GuardianGroup {

    private final ActiveSettings settings;
    private final String totalSamplesGuardName;
    private final long minimumSamples;

    public AbstractGuardianGroup(ActiveSettings settings, String totalSamplesGuardName, long minimumSamples) {
        this.settings = settings;
        this.totalSamplesGuardName = totalSamplesGuardName;
        this.minimumSamples = minimumSamples;
    }

    abstract List<? extends Guard> candidateGuards(Guard.ProfileInfo profileInfo);

    @Override
    public List<GuardianResult> execute(Config config, EventSummary eventSummary, Preconditions preconditions) {
        Guard.ProfileInfo profileInfo = new Guard.ProfileInfo(config.primaryId(), config.eventType());
        List<? extends Guard> candidateGuards = candidateGuards(profileInfo);

        if (eventSummary.samples() >= minimumSamples) {
            List<? extends Guard> guards = candidateGuards.stream()
                    .filter(guard -> guard.initialize(preconditions))
                    .toList();

            Frame frame = JdkRecordingIterators.automaticAndCollect(
                    config.primaryRecordings(),
                    EventProcessors.resolve(config),
                    FrameCollector.IDENTITY);

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
