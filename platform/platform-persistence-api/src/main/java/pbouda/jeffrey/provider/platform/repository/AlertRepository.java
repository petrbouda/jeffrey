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

package pbouda.jeffrey.provider.platform.repository;

import pbouda.jeffrey.shared.common.model.ImportantMessage;

import java.time.Instant;
import java.util.List;

/**
 * Repository for persisting and querying Alert events.
 * Scoped to a specific project.
 */
public interface AlertRepository extends RetentionCleanup {

    /**
     * Inserts an alert. Uses ON CONFLICT DO NOTHING on the dedup index
     * (session_id, type, created_at) for idempotent inserts during replay.
     *
     * @param message the alert to insert
     */
    void insert(ImportantMessage message);

    /**
     * Finds all alerts for the project within the given time range,
     * ordered by created_at DESC (newest first).
     *
     * @param from start of the time range (inclusive)
     * @param to   end of the time range (inclusive)
     * @return list of alerts within the range
     */
    List<ImportantMessage> findAll(Instant from, Instant to);

    /**
     * Deletes all alerts for the project.
     * Used during project deletion cleanup.
     */
    void deleteByProject();
}
