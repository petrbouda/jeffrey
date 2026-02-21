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

package pbouda.jeffrey.provider.platform.repository;

import java.time.Instant;

/**
 * Narrow interface for data retention cleanup operations.
 * Deletes records older than a given cutoff across all projects.
 */
public interface RetentionCleanup {

    /**
     * Deletes all records older than the cutoff across all projects.
     * Used for data retention cleanup.
     *
     * @param cutoff records with {@code created_at} before this instant are deleted
     * @return the number of deleted records
     */
    int deleteOlderThan(Instant cutoff);
}
