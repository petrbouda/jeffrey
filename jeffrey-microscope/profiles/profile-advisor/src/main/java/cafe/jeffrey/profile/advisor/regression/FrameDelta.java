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

package cafe.jeffrey.profile.advisor.regression;

/**
 * How one frame's share of a profile moved between two recordings.
 *
 * <p>The delta is in percentage points, not as a ratio. A frame going from 0.1% to 0.3% has tripled,
 * which sounds alarming and is almost always noise; the same frame going from 9% to 23% is what
 * actually matters. Percentage points say that directly, and a ratio does not.</p>
 *
 * @param name             the frame, as both profiles spell it
 * @param baselineSelfPct  its self share in the baseline recording
 * @param currentSelfPct   its self share in the current recording
 */
public record FrameDelta(String name, double baselineSelfPct, double currentSelfPct) {

    public double deltaPp() {
        return currentSelfPct - baselineSelfPct;
    }

    public boolean isRegression() {
        return deltaPp() > 0;
    }

    /**
     * True when the frame did not appear in the baseline at all — new cost rather than grown cost, which
     * usually points at a different kind of change.
     */
    public boolean isNew() {
        return baselineSelfPct == 0.0 && currentSelfPct > 0.0;
    }
}
