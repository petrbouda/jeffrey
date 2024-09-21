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

package pbouda.jeffrey.guardian.guard.app;

import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.guardian.Formatter;
import pbouda.jeffrey.guardian.guard.TraversableGuard;
import pbouda.jeffrey.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.guardian.preconditions.Preconditions;
import pbouda.jeffrey.guardian.traverse.MatchingType;
import pbouda.jeffrey.guardian.traverse.ResultType;
import pbouda.jeffrey.guardian.traverse.TargetFrameType;

public class HashMapCollisionGuard extends TraversableGuard {

    public HashMapCollisionGuard(ProfileInfo profileInfo, double threshold) {
        super("HashMap Collisions",
                profileInfo,
                threshold,
                FrameMatchers.suffix("Map$TreeNode#findTreeNode"),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.SAMPLES);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between a total number of samples (" + result.totalValue() + ") and " +
                "samples causing the hash collisions (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Key collision is a common issue in HashMaps. It can lead to performance degradation
                because the time complexity of the operations increases from O(1) to O(n). The keys that have
                the same hashcode are stored in a linked list (it often uses a balanced tree for a bucket with
                a small number of collisions - JEP 180, then the linked list takes a place).
                The more collisions, the longer the time to find the key because the list
                needs to be iterated one item after another.
                <br>
                The Guard keeps an eye only on hash maps that are implemented in OpenJDK.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        } else {
            return """
                    The solution is to reduce the number of collisions. It can be achieved by:
                    <ul>
                        <li>Implementing better hashCode() and equals() methods
                        <li>Using a different data structure
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
