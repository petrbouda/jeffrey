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
 * Format-agnostic capability flags of a {@link RecordingFormat}. Consumers branch on these flags
 * instead of on a concrete event source, so a new format only has to describe itself here to get
 * the right behavior everywhere.
 *
 * @param timestampedEvents     whether events carry real per-sample timestamps; aggregated formats
 *                              (e.g. pprof) stamp every event with the profile-wide time, so
 *                              time-resolved views (timeseries, subsecond) convey no information
 * @param curatedEventSummaries whether the flamegraph event list is restricted to the format's
 *                              curated well-known event types; formats with open-ended dimension
 *                              names expose all recorded event types instead
 */
public record RecordingFormatCapabilities(
        boolean timestampedEvents,
        boolean curatedEventSummaries) {
}
