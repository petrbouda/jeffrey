/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.guardian.guard;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.common.analysis.FramePath;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.guardian.GuardianResult;
import pbouda.jeffrey.guardian.preconditions.Preconditions;
import pbouda.jeffrey.guardian.traverse.Traversable;

import java.math.BigDecimal;
import java.util.List;

public interface Guard extends Traversable {

    record ProfileInfo(String primaryProfileId, Type eventType) {
    }

    record Result(
            Severity severity,
            long totalSamples,
            long observedSamples,
            double ratioResult,
            BigDecimal matchedInPercent,
            double threshold,
            List<Frame> observedFrames) {

        public List<Marker> markers() {
            return observedFrames.stream()
                    .map(of -> new Marker(severity, new FramePath(of.framePath())))
                    .toList();
        }

        public Matched matched() {
            return Matched.severity(severity, matchedInPercent);
        }
    }

    /**
     * The result of the guard evaluation with description and other information to correctly react on the result.
     * Moreover, the result contains the frame which was evaluated and caused the result.
     *
     * @return the result of the guard evaluation
     */
    GuardianResult result();

    /**
     * Returns the preconditions for the guard to evaluated whether the guard should be processed or not.
     *
     * @return the preconditions for the guard
     */
    Preconditions preconditions();

    /**
     * Place for initializing the guard and checking the current preconditions.
     * The method is called before the guard is evaluated. If the guard is not applicable
     * for the current preconditions, the method should return false.
     *
     * @param current the current preconditions
     * @return true if the guard is applicable, false otherwise
     */
    boolean initialize(Preconditions current);
}
