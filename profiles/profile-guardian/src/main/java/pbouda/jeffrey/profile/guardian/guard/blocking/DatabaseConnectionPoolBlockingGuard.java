/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.guardian.guard.blocking;

import pbouda.jeffrey.profile.common.analysis.AnalysisResult;
import pbouda.jeffrey.profile.guardian.Formatter;
import pbouda.jeffrey.profile.guardian.guard.TraversableGuard;
import pbouda.jeffrey.profile.guardian.matcher.FrameMatchers;
import pbouda.jeffrey.profile.guardian.preconditions.Preconditions;
import pbouda.jeffrey.profile.guardian.traverse.MatchingType;
import pbouda.jeffrey.profile.guardian.traverse.ResultType;
import pbouda.jeffrey.profile.guardian.traverse.TargetFrameType;

public class DatabaseConnectionPoolBlockingGuard extends TraversableGuard {

    public DatabaseConnectionPoolBlockingGuard(ProfileInfo profileInfo, double threshold) {
        super("DB Connection Pool Blocking",
                profileInfo,
                threshold,
                FrameMatchers.prefix("com.zaxxer.hikari.")
                        .or(FrameMatchers.prefix("org.apache.commons.dbcp2."))
                        .or(FrameMatchers.prefix("com.mchange.v2.c3p0."))
                        .or(FrameMatchers.prefix("org.apache.tomcat.jdbc.pool.")),
                Category.APPLICATION,
                TargetFrameType.JAVA,
                MatchingType.FULL_MATCH,
                ResultType.WEIGHT);
    }

    @Override
    protected String summary() {
        Result result = getResult();

        String direction = result.severity() == AnalysisResult.Severity.OK ? "lower" : "higher";
        return "The ratio between total blocking time (" + result.totalValue() + ") and " +
                "time spent waiting for database connections (" + result.observedValue() + ") " +
                "is " + direction + " than the threshold (" +
                Formatter.formatRatio(result.ratioResult()) + " / " + result.threshold() + ").";
    }

    @Override
    protected String explanation() {
        return """
                Threads are blocked waiting to acquire a database connection from the connection pool.
                This indicates the pool is exhausted — all connections are in use and new requests must wait.
                This is a common bottleneck in database-heavy applications under load.
                """;
    }

    @Override
    protected String solution() {
        Result result = getResult();
        if (result.severity() == AnalysisResult.Severity.OK) {
            return null;
        } else {
            return """
                    <ul>
                        <li>Increase the connection pool size if the database can handle more connections
                        <li>Optimize slow database queries to release connections faster
                        <li>Reduce connection hold time by closing connections promptly (use try-with-resources)
                        <li>Consider connection pool metrics to understand peak usage and wait times
                    </ul>
                    """;
        }
    }

    @Override
    public Preconditions preconditions() {
        return Preconditions.builder().build();
    }
}
