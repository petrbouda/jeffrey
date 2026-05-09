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
 * BOUNDARY strategy: handles the case where fewer than 9 bytes remain at EOF —
 * not enough to form a complete record header. Truncates the file at the start
 * of the dangling bytes.
 */
public final class TruncatedHeaderStrategy implements RepairStrategy {

    private static final int RECORD_HEADER_SIZE = 9;

    @Override
    public String id() {
        return "truncated-header";
    }

    @Override
    public Phase phase() {
        return Phase.BOUNDARY;
    }

    @Override
    public StrategyOutcome examine(ScanContext ctx) {
        long remaining = ctx.fileSize() - ctx.position();
        if (remaining <= 0 || remaining >= RECORD_HEADER_SIZE) {
            return StrategyOutcome.notApplicable();
        }
        return new StrategyOutcome.Applied(
                List.of(new HprofRepair.TruncateFile(ctx.position())),
                ctx.position(),
                true,
                0,
                "Discarded " + remaining + " stray bytes at EOF (incomplete record header)");
    }
}
