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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * One row of the {@code gc_root} table.
 *
 * {@code rootKind} is the raw HPROF sub-tag byte (see {@link HprofTag.Sub}).
 * {@code threadSerial} and {@code frameIndex} are null for root kinds that
 * don't carry that data (e.g. {@code ROOT_STICKY_CLASS}).
 */
public record GcRootRow(
        long instanceId,
        int rootKind,
        Integer threadSerial,
        Integer frameIndex,
        long fileOffset) {
}
