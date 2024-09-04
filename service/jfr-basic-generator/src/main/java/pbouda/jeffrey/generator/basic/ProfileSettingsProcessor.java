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

package pbouda.jeffrey.generator.basic;

import jdk.jfr.consumer.RecordedEvent;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.generator.basic.info.ExtraInfoBuilder;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

public class ProfileSettingsProcessor implements EventProcessor<ExtraInfoBuilder> {

    private final ExtraInfoBuilder extraInfoBuilder = new ExtraInfoBuilder();

    @Override
    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(Type.ACTIVE_SETTING);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        String nameValue = event.getString("name");
        if ("event".equals(nameValue)) {
            extraInfoBuilder
                    .withCpuSource(EventSource.ASYNC_PROFILER)
                    .withCpuEvent(event.getString("value"));
        } else if ("alloc".equals(nameValue)) {
            extraInfoBuilder
                    .withAllocSource(EventSource.ASYNC_PROFILER)
                    .withAllocEvent(event.getString("value"));
        } else if ("lock".equals(nameValue)) {
            extraInfoBuilder
                    .withLockSource(EventSource.ASYNC_PROFILER)
                    .withLockEvent(event.getString("value"));
        }

        return extraInfoBuilder.isComplete() ? Result.DONE : Result.CONTINUE;
    }

    @Override
    public ExtraInfoBuilder get() {
        return extraInfoBuilder;
    }
}
