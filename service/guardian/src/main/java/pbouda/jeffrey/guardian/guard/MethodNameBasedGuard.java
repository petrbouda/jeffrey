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
import pbouda.jeffrey.common.analysis.FramePath;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.guardian.GuardianResult;
import pbouda.jeffrey.guardian.traverse.Next;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public abstract class MethodNameBasedGuard implements Guard {

    private final String guardName;
    private final String methodName;
    private final FrameType frameType;
    private final ProfileInfo profileInfo;
    private final double thresholdInPercent;

    private long totalSamples = -1;
    private long observedSamples = -1;
    private double ratioResult = -1;
    private Next next = Next.CONTINUE;
    private Severity severity;
    private Frame observedFrame;

    public MethodNameBasedGuard(
            String guardName,
            String methodName,
            FrameType frameType,
            ProfileInfo profileInfo,
            double thresholdInPercent) {

        this.guardName = guardName;
        this.methodName = methodName;
        this.frameType = frameType;
        this.profileInfo = profileInfo;
        this.thresholdInPercent = thresholdInPercent;
    }

    @Override
    public Next traverse(Frame frame) {
        if (totalSamples == -1) {
            totalSamples = frame.totalSamples();
        }

        if (next == Next.CONTINUE) {
            if (isWantedMethod(frame)) {
                this.observedSamples = frame.totalSamples();
                this.ratioResult = (double) this.observedSamples / this.totalSamples;
                this.severity = this.ratioResult > this.thresholdInPercent ? Severity.WARNING : Severity.OK;
                this.observedFrame = frame;
                this.next = Next.DONE;
            }
        }

        return next;
    }

    @Override
    public GuardianResult result() {
        String matchedInPercent = String.format("%.2f", ratioResult * 100);

        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker(severity, new FramePath(observedFrame.framePath())));

        GuardVisualization visualization = GuardVisualization.withTimeseries(
                profileInfo.primaryProfileId(),
                profileInfo.eventType(),
                Matched.severity(severity, new BigDecimal(matchedInPercent)),
                markers);

        GuardAnalysisResult analysisItem = new GuardAnalysisResult(
                this.guardName,
                severity,
                explanation(),
                summary(severity, totalSamples, observedSamples, ratioResult, thresholdInPercent),
                solution(severity),
                matchedInPercent + "%",
                visualization);

        return GuardianResult.of(analysisItem);
    }

    /**
     * Generates the summary of the guard evaluation and reasons for the given severity.
     *
     * @param severity           severity as a result of the given guard.
     * @param totalSamples       total number of samples in the profile.
     * @param observedSamples    number of samples for the given frame crossing the threshold.
     * @param ratioResult        the ratio between the total samples and the observed samples.
     * @param thresholdInPercent the threshold for crossing the warning severity.
     * @return the summary and reason of the guard evaluation.
     */
    protected abstract String summary(
            Severity severity,
            long totalSamples,
            long observedSamples,
            double ratioResult,
            double thresholdInPercent);

    /**
     * A long explanation of the guard and the reasons for the given severity.
     *
     * @return the explanation of the guard.
     */
    protected abstract String explanation();

    /**
     * Describes the solution for the given severity and guard.
     *
     * @param severity severity as a result of the given guard.
     * @return the solution for the given severity.
     */
    protected abstract String solution(Severity severity);

    private boolean isWantedMethod(Frame frame) {
        return frame.methodName().equals(methodName) && frame.frameType() == frameType;
    }
}
