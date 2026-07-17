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

package cafe.jeffrey.provider.profile.api;

/**
 * Maps a concrete event-type code to the logical flamegraph category it belongs to
 * (e.g. {@code EXECUTION}, {@code ALLOCATION}). Formats name their sample dimensions
 * differently, so the mapping is provided per {@link RecordingFormat}; the UI groups
 * event summaries by the resolved category without hard-coding any format's codes.
 */
@FunctionalInterface
public interface EventCategoryResolver {

    /**
     * @param eventTypeCode a full event-type code (e.g. {@code pprof.cpu})
     * @return the logical category name, never {@code null} (formats define their own fallback)
     */
    String resolve(String eventTypeCode);
}
