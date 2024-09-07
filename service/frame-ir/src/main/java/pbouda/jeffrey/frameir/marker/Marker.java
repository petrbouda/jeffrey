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

package pbouda.jeffrey.frameir.marker;

import pbouda.jeffrey.common.analysis.FramePath;

import java.util.List;

public record Marker(MarkerType markerType, FramePath path) {

    public static Marker empty() {
        return new Marker(null, new FramePath(List.of()));
    }

    public static Marker warnings(FramePath path) {
        return new Marker(MarkerType.WARNING, path);
    }

    public static List<Marker> warnings(List<FramePath> paths) {
        return paths.stream()
                .map(Marker::warnings)
                .toList();
    }
}
