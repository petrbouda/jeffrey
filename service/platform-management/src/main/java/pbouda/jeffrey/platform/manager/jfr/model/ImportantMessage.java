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

package pbouda.jeffrey.platform.manager.jfr.model;

import java.time.Instant;

/**
 * Represents an ImportantMessage event parsed from a JFR repository.
 * Matches the frontend ImportantMessage interface and the jeffrey.ImportantMessage JFR event.
 *
 * @param type        identifier for this type of message (e.g., HIGH_CPU_USAGE)
 * @param title       short summary of the message
 * @param message     detailed description
 * @param severity    severity level (CRITICAL, HIGH, MEDIUM, LOW)
 * @param category    category of the message (e.g., PERFORMANCE, RESOURCE)
 * @param source      component or service that raised the message
 * @param isAlert     whether the message is intended to be processed as an alert
 * @param sessionId   session ID this message belongs to
 * @param sessionName human-readable session name
 * @param createdAt   when the message was emitted
 */
public record ImportantMessage(
        String type,
        String title,
        String message,
        Severity severity,
        String category,
        String source,
        boolean isAlert,
        String sessionId,
        Instant createdAt) {
}
