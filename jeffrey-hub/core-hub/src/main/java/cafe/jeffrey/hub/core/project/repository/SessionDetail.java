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
package cafe.jeffrey.hub.core.project.repository;

/**
 * How much of a session a listing loads. {@link #HEADERS} is the rows — enough to pick a
 * session by id, status or age. {@link #WITH_FILES} walks the session's directory on the
 * volume as well, which is a stat per file over a network mount: what
 * {@code openRecording()}, {@code finishedRecordings()}, {@code totalSizeBytes()} and
 * {@code isFailedEmpty()} answer from, and meaningless without.
 */
public enum SessionDetail {
    HEADERS,
    WITH_FILES;

    public boolean withFiles() {
        return this == WITH_FILES;
    }
}
