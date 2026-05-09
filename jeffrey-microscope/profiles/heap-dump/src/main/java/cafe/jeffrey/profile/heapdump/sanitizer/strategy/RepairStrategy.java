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

package cafe.jeffrey.profile.heapdump.sanitizer.strategy;

import java.io.IOException;

/**
 * One strategy per HPROF corruption use case. The planner consults each strategy
 * at the appropriate phase; the first strategy that returns {@link StrategyOutcome.Applied}
 * wins for that phase, and the planner advances accordingly.
 *
 * <p>Strategies are pure detectors: they never mutate the file. Mutation happens
 * later, when {@link cafe.jeffrey.profile.heapdump.sanitizer.HprofInPlaceSanitizer}
 * applies the produced {@link cafe.jeffrey.profile.heapdump.sanitizer.HprofRepair} ops.
 */
public sealed interface RepairStrategy permits
        TruncatedHeaderStrategy,
        ZeroLengthSegmentStrategy,
        OverflowedSegmentStrategy,
        TruncatedSubRecordsStrategy,
        TruncatedNonHeapRecordStrategy,
        MissingEndMarkerStrategy {

    /**
     * Stable identifier used in repair-log entries and diagnostics.
     */
    String id();

    /**
     * The phase at which the planner consults this strategy.
     */
    Phase phase();

    /**
     * Examine the scan state. Returns {@link StrategyOutcome#notApplicable()} when
     * this strategy doesn't apply.
     */
    StrategyOutcome examine(ScanContext ctx) throws IOException;

    enum Phase {
        /** Consulted before reading a record header (e.g., when fewer than 9 bytes remain). */
        BOUNDARY,
        /** Consulted after a record header has been parsed. */
        RECORD,
        /** Consulted exactly once after the scan completes. */
        FINALIZE
    }
}
