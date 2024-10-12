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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import pbouda.jeffrey.common.analysis.AutoAnalysisResult;
import pbouda.jeffrey.guardian.guard.GuardAnalysisResult;
import pbouda.jeffrey.guardian.guard.GuardVisualization;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface GuardianManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, GuardianManager> {
    }

    /**
     * Returns the result of flamegraph's analysis in the form of {@link AutoAnalysisResult} that is compatible with
     * visualization components same as {@link AutoAnalysisManager} has.
     *
     * @return list of analysis items that represent the results of the guardian analysis
     */
    List<GuardAnalysisResult> guardResults();

    /**
     * Generates a flamegraph based on the provided {@link GuardVisualization} request.
     *
     * @param visualization data from guard visualization to generate a flamegraph with warnings.
     * @return JSON representation of the flamegraph
     */
    JsonNode generateFlamegraph(GuardVisualization visualization);

    /**
     * Generates a timeseries based on the provided {@link GuardVisualization} request.
     *
     * @param visualization data from guard visualization to generate a timeseries with split series
     *                      (one with 'warning' samples, another one with the rest samples).
     * @return JSON representation of the timeseries
     */
    ArrayNode generateTimeseries(GuardVisualization visualization);
}
