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
import pbouda.jeffrey.guardian.matcher.FrameMatcher;
import pbouda.jeffrey.guardian.preconditions.Preconditions;
import pbouda.jeffrey.guardian.traverse.AbstractTraversable;
import pbouda.jeffrey.guardian.traverse.CurrentFrameTraverser;
import pbouda.jeffrey.guardian.traverse.Next;
import pbouda.jeffrey.guardian.traverse.Traversable;

import java.math.BigDecimal;
import java.util.List;

import static pbouda.jeffrey.guardian.traverse.Next.DONE;

/**
 * Finds a base frame in the tree and starts traversing the tree from
 * the base frame to find the selected frames. Once the base frame is found,
 * then traversals are performed to find the selected frames, then the
 * processing is marked as DONE.
 */
public abstract class TraversableGuard extends AbstractTraversable implements Guard {

    private final String guardName;
    private final ProfileInfo profileInfo;
    private final double threshold;
    private final Category category;

    private Result result;

    private Next globalNext = Next.CONTINUE;
    private boolean applicable = true;

    public TraversableGuard(
            String guardName,
            ProfileInfo profileInfo,
            double threshold,
            FrameMatcher baseFrameMatcher,
            Category category,
            boolean skipJavaFrames) {

        this(guardName,
                profileInfo,
                threshold,
                baseFrameMatcher,
                category,
                List.of(new CurrentFrameTraverser()),
                skipJavaFrames);
    }

    public TraversableGuard(
            String guardName,
            ProfileInfo profileInfo,
            double threshold,
            FrameMatcher baseFrameMatcher,
            Category category,
            List<Traversable> traversables,
            boolean skipJavaFrames) {

        super(baseFrameMatcher, traversables, skipJavaFrames);

        this.guardName = guardName;
        this.profileInfo = profileInfo;
        this.threshold = threshold;
        this.category = category;
    }

    @Override
    public boolean initialize(Preconditions current) {
        this.applicable = preconditions().matches(current);
        if (!this.applicable) {
            globalNext = DONE;
        }
        return applicable;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext == DONE) {
            return DONE;
        }

        globalNext = super.traverse(frame);
        if (globalNext == DONE && result == null) {
            this.result = evaluateFrames(getTotalSamples(), this.threshold, selectedFrames());
        }
        return globalNext;
    }

    private static Result evaluateFrames(long totalSamples, double threshold, List<Frame> frames) {
        long observedSamples = 0;
        for (Frame frame : frames) {
            observedSamples += frame.totalSamples();
        }

        double ratioResult = (double) observedSamples / totalSamples;
        Severity severity = ratioResult > threshold ? Severity.WARNING : Severity.OK;

        BigDecimal matchedInPercent = new BigDecimal(String.format("%.2f", ratioResult * 100));
        return new Result(severity, totalSamples, observedSamples, ratioResult, matchedInPercent, threshold, frames);
    }

    protected Result getResult() {
        return result;
    }

    @Override
    public GuardianResult result() {
        if (!this.applicable) {
            return GuardianResult.notApplicable(guardName, category);
        }

        GuardVisualization visualization = GuardVisualization.withTimeseries(
                profileInfo.primaryProfileId(),
                profileInfo.eventType(),
                result.matched(),
                result.markers());

        GuardAnalysisResult analysisItem = new GuardAnalysisResult(
                guardName,
                result.severity(),
                explanation(),
                summary(),
                solution(),
                result.matchedInPercent() + "%",
                category,
                visualization);

        return GuardianResult.of(analysisItem);
    }

    /**
     * Generates the summary of the guard evaluation and reasons for the given severity.
     *
     * @return the summary and reason of the guard evaluation.
     */
    protected abstract String summary();

    /**
     * A long explanation of the guard and the reasons for the given severity.
     *
     * @return the explanation of the guard.
     */
    protected abstract String explanation();

    /**
     * Describes the solution for the given severity and guard.
     *
     * @return the solution for the given severity.
     */
    protected abstract String solution();
}
