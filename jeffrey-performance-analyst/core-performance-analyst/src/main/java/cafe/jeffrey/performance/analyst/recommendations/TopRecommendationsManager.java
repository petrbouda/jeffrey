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

package cafe.jeffrey.performance.analyst.recommendations;

import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.TopSeverityRecommendation;

import java.util.List;

/**
 * Builds the Overview "Highest Impact" payload from stored recommendations: the per-severity recording
 * counts and the severity-ranked rows, each with a short dominant-hotspot headline derived from the
 * recommendation markdown. Read-only over local SQLite, so it works regardless of whether AI is currently
 * configured.
 */
public class TopRecommendationsManager {

    private static final int MAX_HEADLINE_LENGTH = 140;
    private static final String SUMMARY_HEADING = "summary";

    private final GeneratedRecommendationRepository recommendationRepository;

    public TopRecommendationsManager(GeneratedRecommendationRepository recommendationRepository) {
        this.recommendationRepository = recommendationRepository;
    }

    public TopSeverityOverviewResponse overview(int limit) {
        SeverityCounts counts = SeverityCounts.from(recommendationRepository.countBySeverity());
        List<TopSeverityResponse> items = recommendationRepository.findTopBySeverity(limit).stream()
                .map(row -> TopSeverityResponse.from(row, headline(row)))
                .toList();
        return new TopSeverityOverviewResponse(counts, items);
    }

    /**
     * A short one-liner for the list row: the first meaningful line of the recommendations markdown,
     * with leading markdown heading/list markers removed and a generic "Summary" heading skipped.
     */
    private static String headline(TopSeverityRecommendation row) {
        String markdown = row.recommendations();
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        for (String rawLine : markdown.split("\n")) {
            String line = rawLine.strip();
            while (line.startsWith("#") || line.startsWith("*") || line.startsWith("-") || line.startsWith(">")) {
                line = line.substring(1).strip();
            }
            if (line.isEmpty() || line.equalsIgnoreCase(SUMMARY_HEADING)) {
                continue;
            }
            return line.length() > MAX_HEADLINE_LENGTH ? line.substring(0, MAX_HEADLINE_LENGTH) + "…" : line;
        }
        return "";
    }
}
