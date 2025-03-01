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

package pbouda.jeffrey.provider.api.model;

import java.util.Optional;

public enum StacktraceType {
    JVM(0),
    JVM_JIT(1),
    JVM_GC(2),
    JVM_JFR(3),
    APPLICATION(100),
    NATIVE(200),
    UNKNOWN(1000);

    private static final StacktraceType[] VALUES = values();

    private final int id;

    StacktraceType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<StacktraceType> fromId(int id) {
        for (StacktraceType tag : VALUES) {
            if (tag.id() == id) {
                return Optional.of(tag);
            }
        }
        return Optional.empty();
    }
}
