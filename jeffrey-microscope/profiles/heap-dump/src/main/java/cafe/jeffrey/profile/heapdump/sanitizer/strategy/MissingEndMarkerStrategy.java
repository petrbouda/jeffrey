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

import cafe.jeffrey.profile.heapdump.sanitizer.HprofRepair;

import java.util.List;

/**
 * FINALIZE strategy: appends a synthetic HEAP_DUMP_END if the scan completed
 * without observing one.
 */
public final class MissingEndMarkerStrategy implements RepairStrategy {

    @Override
    public String id() {
        return "missing-end-marker";
    }

    @Override
    public Phase phase() {
        return Phase.FINALIZE;
    }

    @Override
    public StrategyOutcome examine(ScanContext ctx) {
        if (ctx.sawEndMarker()) {
            return StrategyOutcome.notApplicable();
        }
        return new StrategyOutcome.Applied(
                List.of(new HprofRepair.AppendEndMarker()),
                ctx.position(),
                true,
                0,
                "Appended missing HEAP_DUMP_END marker");
    }
}
