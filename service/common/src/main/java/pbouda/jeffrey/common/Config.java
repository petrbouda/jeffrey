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

package pbouda.jeffrey.common;

import java.nio.file.Path;
import java.time.Instant;

public record Config(
        Type type,
        Path primaryRecording,
        Path secondaryRecording,
        pbouda.jeffrey.common.Type eventType,
        Instant primaryStart,
        Instant secondaryStart,
        AbsoluteTimeRange primaryTimeRange,
        AbsoluteTimeRange secondaryTimeRange,
        String searchPattern,
        boolean threadMode,
        boolean collectWeight) {

    public enum Type {
        PRIMARY, DIFFERENTIAL
    }

    public Config(
            Type type,
            Path primaryRecording,
            pbouda.jeffrey.common.Type eventType,
            Instant primaryStart,
            AbsoluteTimeRange primaryTimeRange,
            String searchPattern,
            boolean threadMode,
            boolean collectWeight) {

        this(type, primaryRecording, null, eventType, primaryStart, null, primaryTimeRange, null, searchPattern, threadMode, collectWeight);
    }

    public static ConfigBuilder<?> primaryBuilder() {
        return new ConfigBuilder<>();
    }

    public static DiffConfigBuilder differentialBuilder() {
        return new DiffConfigBuilder();
    }
}
