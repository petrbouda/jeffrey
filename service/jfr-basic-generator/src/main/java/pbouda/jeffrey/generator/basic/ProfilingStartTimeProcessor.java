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
import pbouda.jeffrey.common.EventNotFoundException;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.jfrparser.jdk.SingleEventProcessor;

import java.time.Instant;
import java.util.function.Supplier;

public class ProfilingStartTimeProcessor extends SingleEventProcessor<Instant> {

    private Instant profilingStartTime = null;

    public ProfilingStartTimeProcessor() {
        super(Type.ACTIVE_RECORDING);
    }

    @Override
    public Result onEvent(RecordedEvent event) {
        this.profilingStartTime = event.getInstant("recordingStart");
        return Result.DONE;
    }

    @Override
    public void onComplete() {
        if (profilingStartTime == null) {
            throw new EventNotFoundException("An expected event was not found: " + eventType().code());
        }
    }

    @Override
    public Instant get() {
        return profilingStartTime;
    }
}
