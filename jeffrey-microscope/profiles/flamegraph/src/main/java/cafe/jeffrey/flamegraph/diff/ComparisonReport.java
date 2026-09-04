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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.ai.WeightContext;
import cafe.jeffrey.shared.common.model.Type;

import java.util.List;

/**
 * What moved between two profiles: the methods that grew, the ones that shrank, and the pairs that may
 * simply have been renamed.
 * <p>
 * The ranked answer to "did my change make it worse", as opposed to the diff tree, which is where a
 * reader goes once they know which method to look at.
 *
 * @param eventType        what was compared
 * @param weightContext    what the numbers are measured in
 * @param scale            the time-base correction applied, and every reason to distrust it
 * @param regressed        methods the primary spends more in, heaviest movement first
 * @param improved         methods the primary spends less in, largest saving first
 * @param renameCandidates appeared/vanished pairs of similar size — possibly the same work renamed
 * @param methodsCompared  how many distinct methods carried work in either profile
 */
public record ComparisonReport(
        Type eventType,
        WeightContext weightContext,
        ComparisonScale scale,
        List<MethodDelta> regressed,
        List<MethodDelta> improved,
        List<RenameCandidate> renameCandidates,
        int methodsCompared) {

    public ComparisonReport {
        regressed = List.copyOf(regressed);
        improved = List.copyOf(improved);
        renameCandidates = List.copyOf(renameCandidates);
    }

    /**
     * True when nothing carried work in either profile — an event type neither recording captured,
     * which is a different answer from "nothing changed".
     */
    public boolean empty() {
        return methodsCompared == 0;
    }
}
