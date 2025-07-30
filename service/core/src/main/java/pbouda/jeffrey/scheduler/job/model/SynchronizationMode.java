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

package pbouda.jeffrey.scheduler.job.model;

import java.util.List;

public enum SynchronizationMode {
    /**
     * Synchronization mode that only creates new projects.
     * Existing projects will not be updated or deleted.
     */
    CREATE_ONLY,

    /**
     * Synchronization mode that creates new projects,
     * but also removes projects that are no longer present in the source.
     */
    FULL_SYNC;

    private static final List<SynchronizationMode> VALUES = List.of(values());

    public static SynchronizationMode fromString(String mode) {
        if (mode == null) {
            return null;
        }
        return VALUES.stream()
                .filter(value -> value.name().equalsIgnoreCase(mode))
                .findFirst()
                .orElse(null);
    }
}
