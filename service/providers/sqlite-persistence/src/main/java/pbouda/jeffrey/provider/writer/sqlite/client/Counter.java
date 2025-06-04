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

package pbouda.jeffrey.provider.writer.sqlite.client;

import pbouda.jeffrey.provider.api.streamer.model.*;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

public class Counter implements Consumer<Object> {

    private final LongAdder rows = new LongAdder();
    private final LongAdder samples = new LongAdder();

    @Override
    public void accept(Object o) {
        long collectedSamples = switch (o) {
            case GenericRecord record -> record.samples();
            case FlamegraphRecord record -> record.samples();
            case TimeseriesRecord record -> {
                long samples = 0;
                for (SecondValue value : record.values()) {
                    samples += value.value();
                }
                yield samples;
            }
            case SubSecondRecord record -> record.value();
            default -> 1;
        };

        samples.add(collectedSamples);
        rows.increment();
    }

    public long rows() {
        return rows.longValue();
    }

    public long samples() {
        return samples.longValue();
    }
}
