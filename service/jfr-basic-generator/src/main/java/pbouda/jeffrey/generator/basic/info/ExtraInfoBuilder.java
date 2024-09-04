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

import pbouda.jeffrey.common.EventSource;

public class ExtraInfoBuilder {
    private EventSource cpuSource;
    private EventSource lockSource;
    private EventSource allocSource;

    private String cpuEvent;
    private String lockEvent;
    private String allocEvent;

    public ExtraInfoBuilder withCpuSource(EventSource cpuSource) {
        this.cpuSource = cpuSource;
        return this;
    }

    public ExtraInfoBuilder withLockSource(EventSource lockSource) {
        this.lockSource = lockSource;
        return this;
    }

    public ExtraInfoBuilder withAllocSource(EventSource allocSource) {
        this.allocSource = allocSource;
        return this;
    }

    public ExtraInfoBuilder withCpuEvent(String cpuEvent) {
        this.cpuEvent = cpuEvent;
        return this;
    }

    public ExtraInfoBuilder withLockEvent(String lockEvent) {
        this.lockEvent = lockEvent;
        return this;
    }

    public ExtraInfoBuilder withAllocEvent(String allocEvent) {
        this.allocEvent = allocEvent;
        return this;
    }

    public boolean isComplete() {
        return cpuSource != null
                && lockSource != null
                && allocSource != null
                && cpuEvent != null
                && lockEvent != null
                && allocEvent != null;
    }

    public ExtraInfo build() {
        return new ExtraInfo(cpuSource, lockSource, allocSource, cpuEvent, lockEvent, allocEvent);
    }
}
