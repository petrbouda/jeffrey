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

package cafe.jeffrey.shared.common.activity;

/**
 * The one statement of every bound the event-activity feature enforces. Hub applies them while
 * scanning; Microscope applies the same numbers before a request leaves the machine, so a rejection
 * is explained locally rather than as a remote error. They live here because a copy on either side
 * would let the two drift silently — Microscope admitting what Hub refuses, or refusing what Hub
 * would have answered.
 */
public final class ActivityLimits {

    /** Buckets covering the requested window. Caps counter memory independently of event volume. */
    public static final int MAX_BUCKETS = 288;

    /** Distinct event types a single scan may observe before it gives up and asks for a filter. */
    public static final int MAX_OBSERVED_TYPES = 512;

    /** Explicit event-type names a caller may pass. */
    public static final int MAX_FILTER_TYPES = 16;

    public static final int MAX_TYPE_LENGTH = 256;

    /** Workspace, project, session and scan identifiers. */
    public static final int MAX_ID_LENGTH = 512;

    /** Buckets returned by one poll. Totals still account for the ones left out. */
    public static final int MAX_RESULT_BUCKETS = 20;

    /** Event types detailed per returned bucket; the rest are counted in {@code omittedTypes}. */
    public static final int MAX_RESULT_TYPES = 10;

    public static final long DEFAULT_BUCKET_SECONDS = 300;

    public static final long MILLIS_PER_SECOND = 1000;

    private ActivityLimits() {
    }
}
