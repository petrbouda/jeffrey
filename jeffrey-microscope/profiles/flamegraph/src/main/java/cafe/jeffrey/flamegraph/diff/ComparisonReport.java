/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.microscope.model.Type;

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
