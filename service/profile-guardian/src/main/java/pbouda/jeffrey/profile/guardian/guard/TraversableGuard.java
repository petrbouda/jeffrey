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
import pbouda.jeffrey.profile.guardian.matcher.FrameMatcher;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Supplier;

import static pbouda.jeffrey.profile.guardian.traverse.Next.DONE;
import static pbouda.jeffrey.profile.guardian.traverse.Next.NOT_STARTED;

/**
 * Finds a base frame in the tree and starts traversing the tree from
 * the base frame to find the selected frames. Once the base frame is found,
 * then traversals are performed to find the selected frames, then the
 * processing is marked as DONE.
 */
public abstract class TraversableGuard extends AbstractTraversable implements Guard {
    private static final MathContext PRECISION_CONTEXT = new MathContext(2, RoundingMode.HALF_UP);

    private final String guardName;
    private final ProfileInfo profileInfo;
    private final double threshold;
    private final Category category;
    private final ResultType resultType;

    private Result result;

    private Next globalNext = NOT_STARTED;
    private boolean applicable = true;

    public TraversableGuard(
            String guardName,
            ProfileInfo profileInfo,
            double threshold,
            FrameMatcher baseFrameMatcher,
            Category category,
            TargetFrameType targetFrameType,
            MatchingType matchingType,
            ResultType resultType) {

        this(guardName,
                profileInfo,
                threshold,
                baseFrameMatcher,
                category,
                () -> List.of(new CurrentFrameTraverser()),
                targetFrameType,
                matchingType,
                resultType);
    }

    public TraversableGuard(
            String guardName,
            ProfileInfo profileInfo,
            double threshold,
            FrameMatcher baseFrameMatcher,
            Category category,
            Supplier<List<Traversable>> traversables,
            TargetFrameType targetFrameType,
            MatchingType matchingType,
            ResultType resultType) {

        super(baseFrameMatcher, traversables, targetFrameType, matchingType);

        this.guardName = guardName;
        this.profileInfo = profileInfo;
        this.threshold = threshold;
        this.category = category;
        this.resultType = resultType;
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
        if (globalNext == Next.NOT_STARTED) {
            globalNext = Next.CONTINUE;
        }

        if (globalNext == DONE) {
            return DONE;
        }

        globalNext = super.traverse(frame);
        return globalNext;
    }

    private Result evaluateFrames() {
        long totalValue = ResultType.SAMPLES == resultType ? getTotalSamples() : getTotalWeight();
        long observedValue = 0;
        List<Frame> frames = selectedFrames();

        for (Frame frame : frames) {
            if (resultType == ResultType.SAMPLES) {
                observedValue += frame.totalSamples();
            } else {
                observedValue += frame.totalWeight();
            }
        }

        double ratioResult = totalValue != 0 ? (double) observedValue / totalValue : 0;
        Severity severity = ratioResult > threshold ? Severity.WARNING : Severity.OK;

        BigDecimal matchedInPercent = new BigDecimal(ratioResult * 100, PRECISION_CONTEXT);
        return new Result(severity, totalValue, observedValue, ratioResult, matchedInPercent, threshold, frames);
    }

    protected Result getResult() {
        return result;
    }

    @Override
    public GuardianResult result() {
        // Applicable
        //   - the groups works well but this concrete guard does not fulfill preconditions to be executed
        //   - e.g. different Garbage Collector
        // NOT_STARTED
        //   - guard even not started
        //   - e.g. insufficient number of samples
        if (!this.applicable || globalNext == NOT_STARTED) {
            return GuardianResult.notApplicable(guardName, category);
        }

        this.result = evaluateFrames();

        GuardVisualization visualization = GuardVisualization.withTimeseries(
                profileInfo.primaryProfileId(),
                profileInfo.eventType(),
                resultType == ResultType.WEIGHT,
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
