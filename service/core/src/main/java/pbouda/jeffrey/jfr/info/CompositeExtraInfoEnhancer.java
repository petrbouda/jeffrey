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

package pbouda.jeffrey.jfr.info;

import jdk.jfr.EventType;
import pbouda.jeffrey.jfr.ProfileSettingsProcessor;
import pbouda.jeffrey.jfr.configuration.JsonContent;
import pbouda.jeffrey.jfr.event.EventSummary;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;

import java.nio.file.Path;
import java.util.List;

public class CompositeExtraInfoEnhancer implements ExtraInfoEnhancer {

    private final Path recording;

    private List<ExtraInfoEnhancer> enhancers;

    public CompositeExtraInfoEnhancer(Path recording) {
        this.recording = recording;
    }

    public void initialize() {
        var settings = RecordingIterators.singleAndCollectIdentical(recording, new ProfileSettingsProcessor());

        this.enhancers = List.of(
                new ExecutionSamplesExtraInfo(settings),
                new AllocationSamplesExtraInfo(settings),
                new BlockingExtraInfo(settings)
        );
    }

    @Override
    public boolean isApplicable(EventType eventType) {
        return true;
    }

    @Override
    public EventSummary apply(EventSummary eventSummary) {
        EventSummary current = eventSummary;
        for (ExtraInfoEnhancer enhancer : enhancers) {
            if (enhancer.isApplicable(current.eventType())) {
                current = enhancer.apply(current);
            }
        }
        return current;
    }
}
