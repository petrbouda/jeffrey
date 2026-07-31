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

package cafe.jeffrey.profile.advisor.prompt;

/**
 * One frame of a parsed profile, flattened out of the {@link cafe.jeffrey.frameir.Frame} tree and kept
 * alongside the cached prompt markdown. This is the machine-readable counterpart of a line in the
 * markdown call tree: the same numbers the model is shown, in a form Java can check claims against.
 *
 * <p>Frames are aggregated by method name across every call path they appear on, because that is the
 * granularity a recommendation cites — a model says "{@code RateTable.lookup} is hot", not "this
 * particular path through {@code RateTable.lookup}".</p>
 *
 * @param name               the frame's method name exactly as it appears in the profile
 * @param totalPct           the frame's subtree weight as a percentage of the profile's total samples
 * @param selfPct            the share that stayed at this exact frame, as a percentage of total samples
 * @param totalSamples       the frame's subtree weight in samples
 * @param interpretedSamples samples recorded while the frame ran interpreted
 * @param c1Samples          samples recorded while the frame ran C1-compiled
 * @param c2Samples          samples recorded while the frame ran C2-compiled
 */
public record ProfileFrame(
        String name,
        double totalPct,
        double selfPct,
        long totalSamples,
        long interpretedSamples,
        long c1Samples,
        long c2Samples) {

    public ProfileFrame {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Frame name must not be blank");
        }
    }

    /**
     * True when the frame carries meaningful C1 weight and never reached C2 — the signal that a method
     * is hot enough to matter but is not being promoted by the JIT.
     */
    public boolean stuckAtC1() {
        return c1Samples > 0 && c2Samples == 0;
    }

    /**
     * True when most of the frame's cost is in the frame itself rather than in what it calls. A leaf
     * hotspot is fixed by changing this method; an orchestration frame is fixed further down.
     */
    public boolean isLeafHotspot() {
        return totalPct > 0 && selfPct / totalPct >= 0.5;
    }
}
