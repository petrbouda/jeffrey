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

package cafe.jeffrey.profile.guardian.definition;

import cafe.jeffrey.profile.guardian.matcher.ExpressionFrameMatcher;
import cafe.jeffrey.profile.guardian.matcher.FrameMatcher;
import cafe.jeffrey.profile.guardian.traverse.BaseWithMatcherTraverser;
import cafe.jeffrey.profile.guardian.traverse.CurrentFrameTraverser;
import cafe.jeffrey.profile.guardian.traverse.NameBasedSingleTraverser;
import cafe.jeffrey.profile.guardian.traverse.Traversable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Turns the declarative {@link MatchExpr} / {@link TraversalStrategy} specs into the runtime
 * {@link FrameMatcher} / {@link Traversable} objects that {@code TraversableGuard} expects. This is the
 * one place that bridges the data-driven model and the existing traversal machinery.
 */
public final class GuardSpecFactory {

    private GuardSpecFactory() {
    }

    /** Builds a {@link FrameMatcher} from a generic predicate tree. */
    public static FrameMatcher toMatcher(MatchExpr expr) {
        return switch (expr) {
            case MatchExpr.Predicate(MatchOp op, String value) -> new ExpressionFrameMatcher(op, value);
            case MatchExpr.AnyOf(List<MatchExpr> of) -> of.stream()
                    .map(GuardSpecFactory::toMatcher)
                    .reduce(FrameMatcher::or)
                    .orElseThrow();
            case MatchExpr.AllOf(List<MatchExpr> of) -> of.stream()
                    .map(GuardSpecFactory::toMatcher)
                    .reduce(FrameMatcher::and)
                    .orElseThrow();
            case MatchExpr.Not(MatchExpr inner) -> toMatcher(inner).negate();
        };
    }

    /** Builds the supplier of traversers that walk from the anchor frame to the observed frames. */
    public static Supplier<List<Traversable>> toTraversables(TraversalStrategy strategy) {
        return switch (strategy) {
            case TraversalStrategy.CurrentFrame ignored -> () -> List.of(new CurrentFrameTraverser());
            case TraversalStrategy.Descend(List<TraversalStrategy.Step> steps) ->
                    () -> steps.stream().map(GuardSpecFactory::toTraverser).toList();
        };
    }

    private static Traversable toTraverser(TraversalStrategy.Step step) {
        return switch (step) {
            case TraversalStrategy.Step.ByName(String frameName) -> new NameBasedSingleTraverser(frameName);
            case TraversalStrategy.Step.ByMatcher(MatchExpr base, MatchExpr target) ->
                    new BaseWithMatcherTraverser(toMatcher(base), toMatcher(target));
        };
    }
}
