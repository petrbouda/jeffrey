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

package cafe.jeffrey.profile.thread;

import java.util.List;

/**
 * One event behind a timeline band, with the field values the tooltip renders. The values are
 * positional and line up with the fields declared for the matching state in
 * {@link ThreadMetadata} — the same contract the timeline has always used, only fetched per band
 * instead of shipped with every event.
 *
 * @param startOffset offset of the event from the beginning of the recording, in nanoseconds
 * @param width       length of the event in nanoseconds
 * @param values      field values, in the order the state's metadata declares them
 */
public record ThreadEventDetail(long startOffset, long width, List<Object> values) {
}
