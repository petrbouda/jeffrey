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

package pbouda.jeffrey.generator.basic.info;

import jdk.jfr.EventType;
import pbouda.jeffrey.generator.basic.ProfileSettingsProcessor;
import pbouda.jeffrey.generator.basic.event.EventSummary;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.nio.file.Path;
import java.util.List;

public class CompositeExtraInfoEnhancer implements ExtraInfoEnhancer {

    private List<ExtraInfoEnhancer> enhancers;

    public void initialize(List<Path> recordings) {
        if (!recordings.isEmpty()) {
            ExtraInfo settings = RecordingIterators.automaticAndCollect(
                    recordings, ProfileSettingsProcessor::new, new ExtraInfoCollector());

            this.enhancers = List.of(
                    new ExecutionSamplesExtraInfo(settings),
                    new AllocationSamplesExtraInfo(settings),
                    new BlockingExtraInfo(settings)
            );
        }
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return true;
    }

    @Override
    public EventSummary apply(EventSummary eventSummary) {
        EventSummary current = eventSummary;
        if (enhancers != null) {
            for (ExtraInfoEnhancer enhancer : enhancers) {
                if (enhancer.isApplicable(current.eventType())) {
                    current = enhancer.apply(current);
                }
            }
        }
        return current;
    }
}
