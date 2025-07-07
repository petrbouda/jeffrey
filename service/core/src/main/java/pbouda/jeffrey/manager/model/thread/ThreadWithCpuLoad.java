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

package pbouda.jeffrey.manager.model.thread;

import pbouda.jeffrey.common.model.ThreadInfo;

import java.math.BigDecimal;

/**
 * A thread that with CPU load information.
 *
 * @param timestamp  the timestamp of the measurement in milliseconds
 * @param threadInfo the thread information including name, OS ID, and Java ID
 * @param cpuLoad    the CPU load of the thread in percent
 */
public record ThreadWithCpuLoad(long timestamp, ThreadInfo threadInfo, BigDecimal cpuLoad) {
}
