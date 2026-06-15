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

package cafe.jeffrey.profile.guardian.guard;

import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.guardian.Formatter;
import cafe.jeffrey.profile.guardian.definition.GuardDefinition;
import cafe.jeffrey.profile.guardian.definition.GuardSpecFactory;
import cafe.jeffrey.profile.guardian.preconditions.Preconditions;

/**
 * A single, data-driven guard whose entire behavior comes from a {@link GuardDefinition} loaded from
 * the database. Replaces the ~60 hand-written {@code TraversableGuard} subclasses: the frame matcher,
 * traversal strategy, category, result type, thresholds, preconditions, and the summary/explanation/
 * solution text are all supplied by the definition.
 */
public class ConfigurableGuard extends TraversableGuard {

    private final GuardDefinition definition;

    public ConfigurableGuard(ProfileInfo profileInfo, GuardDefinition definition) {
        super(definition.name(),
                profileInfo,
                definition.infoThreshold(),
                definition.warningThreshold(),
                GuardSpecFactory.toMatcher(definition.anchor()),
                definition.category(),
                GuardSpecFactory.toTraversables(definition.traversal()),
                definition.targetFrameType(),
                definition.matchingType(),
                definition.resultType());

        this.definition = definition;
    }

    @Override
    public Preconditions preconditions() {
        return definition.preconditions().toPreconditions();
    }

    @Override
    protected String summary() {
        Result result = getResult();
        String direction = result.severity() == Severity.OK ? "lower" : "higher";
        return "The ratio between the total observed value (" + result.totalValue() + ") and the value " +
                "attributed to " + definition.summaryNoun() + " (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return definition.explanation();
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == Severity.OK) {
            return null;
        }
        return definition.solution();
    }
}
