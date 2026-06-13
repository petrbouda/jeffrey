/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.nmt;

/**
 * Native memory accounted to one NMT category ({@code jdk.NativeMemoryUsage.type}), e.g. "Java Heap",
 * "Class", "Thread", "Code", "GC". {@code reservedBytes} is address space; {@code committedBytes} is
 * actually backed by memory. {@code growthBytes} = last committed − first committed across the
 * recording, the leak signal (a steadily growing Thread or Class category points at a leak).
 *
 * @param category            NMT category name
 * @param reservedBytes       latest reserved bytes
 * @param committedBytes      latest committed bytes
 * @param startCommittedBytes committed bytes at the first sample
 * @param growthBytes         committed growth over the recording (may be negative)
 */
public record NmtCategory(
        String category,
        long reservedBytes,
        long committedBytes,
        long startCommittedBytes,
        long growthBytes) {
}
