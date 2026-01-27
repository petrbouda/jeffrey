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

package pbouda.jeffrey.profile.guardian.guard;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;

public record GuardAnalysisResult(
        String rule,
        AnalysisResult.Severity severity,
        String explanation,
        String summary,
        String solution,
        String score,
        Guard.Category category,
        GuardVisualization visualization,
        String group) implements AnalysisResult {

    public GuardAnalysisResult(
            String rule,
            AnalysisResult.Severity severity,
            String explanation,
            String summary,
            String solution,
            String score,
            Guard.Category category,
            GuardVisualization visualization) {
        this(rule, severity, explanation, summary, solution, score, category, visualization, null);
    }

    public GuardAnalysisResult withGroup(String group) {
        return new GuardAnalysisResult(rule, severity, explanation, summary, solution, score, category, visualization, group);
    }

    public static GuardAnalysisResult notApplicable(String rule, Guard.Category category) {
        return new GuardAnalysisResult(rule, AnalysisResult.Severity.NA, null, null, null, null, category, null);
    }
}
