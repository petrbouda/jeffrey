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

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RecordingInformation(
        long sizeInBytes,
        RecordingEventSource eventSource,
        Instant recordingStartedAt,
        Instant recordingFinishedAt) {

    /**
     * One recording's information from its files' — the chunks of a session, in reading order.
     * Sizes add up, the window spans from the earliest start to the latest end, and the event
     * source is the first file's; a chunk without a start or an end does not shrink the window.
     *
     * @throws IllegalArgumentException when there is nothing to merge
     */
    public static RecordingInformation merge(List<RecordingInformation> parts) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A recording has at least one file");
        }
        long size = parts.stream().mapToLong(RecordingInformation::sizeInBytes).sum();
        Instant startedAt = parts.stream()
                .map(RecordingInformation::recordingStartedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant finishedAt = parts.stream()
                .map(RecordingInformation::recordingFinishedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new RecordingInformation(size, parts.getFirst().eventSource(), startedAt, finishedAt);
    }
}
