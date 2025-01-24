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

package pbouda.jeffrey.common.model.profile;

import java.util.Optional;

public enum StacktraceTag {
    IDLE(0),
    UNSAFE_ALLOCATION(1);

    private static final StacktraceTag[] VALUES = values();

    private final int id;

    StacktraceTag(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static Optional<StacktraceTag> fromId(int id) {
        for (StacktraceTag tag : VALUES) {
            if (tag.getId() == id) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }
}
