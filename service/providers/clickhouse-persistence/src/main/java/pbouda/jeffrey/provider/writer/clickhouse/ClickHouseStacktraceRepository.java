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

package pbouda.jeffrey.provider.writer.clickhouse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for fetching aggregated stacktrace data from ClickHouse.
 * This abstracts the ClickHouse-specific query logic from the flamegraph generation.
 */
public interface ClickHouseStacktraceRepository {

    /**
     * Retrieves aggregated stacktrace data for a specific profile and time range.
     *
     * @param profileId the profile identifier
     * @param eventType the JFR event type (e.g., "jdk.ExecutionSample")
     * @param startTime start time in nanoseconds from beginning
     * @param endTime   end time in nanoseconds from beginning
     * @return list of aggregated stacktrace data
     */
    List<ClickHouseStacktrace> getStacktraceAggregates(
            String profileId,
            String eventType,
            long startTime,
            long endTime);

    /**
     * Retrieves aggregated stacktrace data with pattern filtering.
     * This leverages ClickHouse's text search capabilities for efficient filtering.
     *
     * @param profileId     the profile identifier
     * @param eventType     the JFR event type
     * @param startTime     start time in nanoseconds from beginning
     * @param endTime       end time in nanoseconds from beginning
     * @param searchPattern pattern to search in frame class/method names
     * @return list of filtered aggregated stacktrace data
     */
    List<ClickHouseStacktrace> getStacktraceAggregatesByPattern(
            String profileId,
            String eventType,
            long startTime,
            long endTime,
            String searchPattern);

    /**
     * Retrieves aggregated stacktrace data asynchronously for better performance.
     */
    CompletableFuture<List<ClickHouseStacktrace>> getStacktraceAggregatesAsync(
            String profileId,
            String eventType,
            long startTime,
            long endTime);

    /**
     * Retrieves the top N stacktraces by sample count for a given profile and time range.
     *
     * @param profileId the profile identifier
     * @param eventType the JFR event type
     * @param startTime start time in nanoseconds from beginning
     * @param endTime   end time in nanoseconds from beginning
     * @param limit     maximum number of stacktraces to return
     * @return list of top stacktraces ordered by sample count
     */
    List<ClickHouseStacktrace> getTopStacktraces(
            String profileId,
            String eventType,
            long startTime,
            long endTime,
            int limit);

    /**
     * Retrieves stacktrace aggregates for multiple profiles in parallel.
     * Useful for comparative analysis across different profiles.
     *
     * @param profileIds list of profile identifiers
     * @param eventType  the JFR event type
     * @param startTime  start time in nanoseconds from beginning
     * @param endTime    end time in nanoseconds from beginning
     * @return map of profile ID to their aggregated stacktrace data
     */
    CompletableFuture<java.util.Map<String, List<ClickHouseStacktrace>>> getMultiProfileAggregates(
            List<String> profileIds,
            String eventType,
            long startTime,
            long endTime);

    /**
     * Retrieves basic statistics for a profile to help with flamegraph sizing.
     *
     * @param profileId the profile identifier
     * @param eventType the JFR event type
     * @return statistics about the profile data
     */
    ProfileStatistics getProfileStatistics(String profileId, String eventType);

    /**
     * Statistics about profile data useful for flamegraph generation.
     */
    record ProfileStatistics(
            long totalEvents,
            long uniqueStacktraces,
            long totalSamples,
            long totalWeight,
            long minTimestamp,
            long maxTimestamp) {
    }
}
