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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.shared.common.model.Severity;

import java.time.Instant;

/**
 * A snapshot of a recommendation task's progress, serialized to the UI over SSE. {@code severity},
 * {@code recommendations} and {@code patch} are populated only on the terminal
 * {@link RecommendationStatus#COMPLETED} event ({@code patch} stays null when the model proposed no code
 * edit); {@code errorMessage} only on {@link RecommendationStatus#FAILED}.
 */
public record RecommendationProgress(
        String taskId,
        String recordingId,
        String eventType,
        RecommendationStatus status,
        String message,
        Severity severity,
        String recommendations,
        String patch,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {
}
