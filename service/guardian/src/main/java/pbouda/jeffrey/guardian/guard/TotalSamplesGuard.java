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

import pbouda.jeffrey.common.analysis.AnalysisResult.Severity;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.guardian.GuardianResult;
import pbouda.jeffrey.guardian.preconditions.Preconditions;
import pbouda.jeffrey.guardian.traverse.Next;

import java.util.List;

import static pbouda.jeffrey.guardian.traverse.Next.DONE;

public class TotalSamplesGuard implements Guard {

    private final long minTotalSamples;

    private long totalSamplesMeasured;
    private Severity severity;

    private Next globalNext = Next.CONTINUE;
    private boolean applicable = true;

    public TotalSamplesGuard(long minTotalSamples) {
        this.minTotalSamples = minTotalSamples;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext == Next.CONTINUE) {
            globalNext = _evaluate(frame, minTotalSamples);
            totalSamplesMeasured = frame.totalSamples();
            severity = globalNext == Next.TERMINATE_IMMEDIATELY ? Severity.WARNING : Severity.OK;
        }

        return globalNext;
    }

    @Override
    public List<Frame> selectedFrames() {
        return List.of();
    }

    @Override
    public GuardianResult result() {
        GuardAnalysisResult analysisItem = new GuardAnalysisResult(
                "Minimum of Total Samples",
                severity,
                explanation(),
                summary(totalSamplesMeasured, minTotalSamples),
                solution(),
                totalSamplesMeasured + " / " + minTotalSamples,
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
        this.applicable = preconditions().matches(current);
        if (!this.applicable) {
            globalNext = DONE;
        }
        return applicable;
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

    private static Next _evaluate(Frame frame, long minTotalSamples) {
        if (frame.totalSamples() < minTotalSamples) {
            return Next.TERMINATE_IMMEDIATELY;
        } else {
            return Next.DONE;
        }
    }
}
