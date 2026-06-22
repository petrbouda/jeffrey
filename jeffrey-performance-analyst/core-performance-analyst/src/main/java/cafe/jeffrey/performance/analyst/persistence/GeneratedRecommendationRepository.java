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

package cafe.jeffrey.performance.analyst.persistence;

import java.util.List;

public interface GeneratedRecommendationRepository {

    /**
     * The stored recommendation results for a recording, ordered by event type. Empty if none have been
     * generated yet.
     */
    List<GeneratedRecommendation> findByRecording(String recordingId);

    /**
     * Inserts or replaces a recommendation result keyed by (recordingId, eventType).
     */
    void upsert(GeneratedRecommendation recommendation);

    /**
     * The most impactful recordings across all projects: one row per recording (its worst severity and
     * that recommendation's details), ordered by severity then recency, capped at {@code limit}.
     */
    List<TopSeverityRecommendation> findTopBySeverity(int limit);

    /**
     * Counts recordings grouped by their worst recommendation severity, for the Overview tiles.
     */
    List<SeverityCount> countBySeverity();
}
