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

import pbouda.jeffrey.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.profile.guardian.GuardianResult;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.Next;

import java.util.List;

public class TotalSamplesGuard implements Guard {

    private final String guardName;
    private final long measuredSamples;
    private final long minTotalSamples;
    private final Severity severity;

    public TotalSamplesGuard(String guardName, long measuredSamples, long minTotalSamples) {
        this.guardName = guardName;
        this.measuredSamples = measuredSamples;
        this.minTotalSamples = minTotalSamples;

        if (measuredSamples >= minTotalSamples) {
            this.severity = Severity.OK;
        } else {
            this.severity = Severity.WARNING;
        }
    }

    @Override
    public Next traverse(Frame frame) {
        return Next.DONE;
    }

    @Override
    public List<Frame> selectedFrames() {
        return List.of();
    }

    @Override
    public GuardianResult result() {
        GuardAnalysisResult analysisItem = new GuardAnalysisResult(
                this.guardName,
                severity,
                explanation(),
                summary(measuredSamples, minTotalSamples),
                solution(),
                measuredSamples + " / " + minTotalSamples,
                Category.PREREQUISITES,
                null
        );

        return GuardianResult.of(analysisItem);
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.EMPTY;
    }

    @Override
    public boolean initialize(Preconditions current) {
        return true;
    }

    private String summary(long samplesMeasured, long samplesThreshold) {
        if (severity == Severity.OK) {
            return "The total number of samples (" + samplesMeasured + ") is higher or equal than the required " +
                    "threshold (" + samplesThreshold + ")";
        } else {
            return "The total number of samples (" + samplesMeasured + ") is less than the required threshold ("
                    + samplesThreshold + ")";
        }
    }

    private String explanation() {
        if (severity == Severity.OK) {
            return "The observed sufficient number of samples leads to more accurate and reliable results.";
        } else {
            return """
                    A small number of samples can lead to inaccurate results, as the data might not be representative. \
                    It can cause the analysis to miss important details or to provide misleading information. \
                    Increasing the number of samples can help to get more accurate and reliable outputs.
                    """;
        }
    }

    private String solution() {
        if (severity == Severity.OK) {
            return null;
        } else {
            return """
                    Increase the number of samples by running the application for a longer period of time, \
                    increase the sampling frequency, or use a different settings of your load testing tool \
                    (if there is any).
                    """;
        }
    }
}
