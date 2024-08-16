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
import pbouda.jeffrey.jfrparser.jdk.EventProcessor;
import pbouda.jeffrey.jfrparser.jdk.ProcessableEvents;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileSettingsProcessor implements EventProcessor<Map<String, String>> {

    private static final BitSet COMPLETE_BITSET = BitSet.valueOf(new byte[]{1, 1, 1, 1});
    private static final int SOURCE_BIT_INDEX = 0;
    private static final int CPU_EVENT_BIT_INDEX = 1;
    private static final int ALLOC_EVENT_BIT_INDEX = 2;
    private static final int LOCK_EVENT_BIT_INDEX = 3;

    private final BitSet isComplete = new BitSet(4);

    private final Map<String, String> mappedValues = new HashMap<>();

    @Override
    public ProcessableEvents processableEvents() {
        return new ProcessableEvents(List.of(Type.ACTIVE_SETTING, Type.ACTIVE_RECORDING));
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        if (Type.ACTIVE_RECORDING.sameAs(event)) {
            String name = event.getString("name");

            if (name.startsWith("async-profiler")) {
                mappedValues.put("source", EventSource.ASYNC_PROFILER.name());
            } else {
                mappedValues.put("source", EventSource.JDK.name());
            }
            isComplete.set(SOURCE_BIT_INDEX);
        } else {
            String nameValue = event.getString("name");
            if ("event".equals(nameValue)) {
                mappedValues.put("cpu_event", event.getString("value"));
                isComplete.set(CPU_EVENT_BIT_INDEX);
            } else if ("alloc".equals(nameValue)) {
                mappedValues.put("alloc_event", event.getString("value"));
                isComplete.set(ALLOC_EVENT_BIT_INDEX);
            } else if ("lock".equals(nameValue)) {
                mappedValues.put("lock_event", event.getString("value"));
                isComplete.set(LOCK_EVENT_BIT_INDEX);
            }
        }

        if (COMPLETE_BITSET.equals(isComplete)) {
            return Result.DONE;
        } else {
            return Result.CONTINUE;
        }
    }

    @Override
    public Map<String, String> get() {
        return mappedValues;
    }
}
