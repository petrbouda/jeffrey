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

package pbouda.jeffrey.platform.resources.response;

import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.Severity;

import java.time.Instant;

/**
 * Response DTO for ImportantMessage events.
 */
public record ImportantMessageResponse(
        String type,
        String title,
        String message,
        String severity,
        String category,
        String source,
        boolean isAlert,
        String sessionId,
        long createdAt
) {

    public static ImportantMessageResponse from(ImportantMessage msg) {
        return new ImportantMessageResponse(
                msg.type(),
                msg.title(),
                msg.message(),
                msg.severity().name(),
                msg.category(),
                msg.source(),
                msg.isAlert(),
                msg.sessionId(),
                msg.createdAt().toEpochMilli()
        );
    }

    public ImportantMessage toModel() {
        return new ImportantMessage(
                type,
                title,
                message,
                Severity.fromString(severity),
                category,
                source,
                isAlert,
                sessionId,
                Instant.ofEpochMilli(createdAt));
    }
}
