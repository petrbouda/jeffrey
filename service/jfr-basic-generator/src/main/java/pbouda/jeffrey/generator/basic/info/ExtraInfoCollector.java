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

import pbouda.jeffrey.common.Collector;

import java.util.function.Supplier;

public class ExtraInfoCollector implements Collector<ExtraInfoBuilder, ExtraInfo> {

    @Override
    public Supplier<ExtraInfoBuilder> empty() {
        return ExtraInfoBuilder::new;
    }

    @Override
    public ExtraInfoBuilder combiner(ExtraInfoBuilder p1, ExtraInfoBuilder p2) {
        ExtraInfo extraInfo1 = p1.build();
        ExtraInfo extraInfo2 = p2.build();

        ExtraInfoBuilder builder = new ExtraInfoBuilder();
        builder.withCpuSource(extraInfo1.cpuSource() != null ? extraInfo1.cpuSource() : extraInfo2.cpuSource());
        builder.withLockSource(extraInfo1.lockSource() != null ? extraInfo1.lockSource() : extraInfo2.lockSource());
        builder.withAllocSource(extraInfo1.allocSource() != null ? extraInfo1.allocSource() : extraInfo2.allocSource());
        builder.withCpuEvent(extraInfo1.cpuEvent() != null ? extraInfo1.cpuEvent() : extraInfo2.cpuEvent());
        builder.withLockEvent(extraInfo1.lockEvent() != null ? extraInfo1.lockEvent() : extraInfo2.lockEvent());
        builder.withAllocEvent(extraInfo1.allocEvent() != null ? extraInfo1.allocEvent() : extraInfo2.allocEvent());
        return builder;
    }

    @Override
    public ExtraInfo finisher(ExtraInfoBuilder combined) {
        return combined.build();
    }
}
