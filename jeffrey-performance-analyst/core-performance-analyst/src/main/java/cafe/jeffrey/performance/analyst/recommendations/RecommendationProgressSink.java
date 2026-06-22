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

/**
 * Intermediate phase callbacks the {@link RecordingRecommendationManager} uses to report progress while
 * generating recommendations. Kept free of any web/SSE types so the manager stays framework-agnostic;
 * {@link RecommendationTask} implements it. The terminal completed/failed transitions are driven by the
 * controller from the manager's return value (or thrown exception), not through this sink.
 */
public interface RecommendationProgressSink {

    /** The repository is being cloned. */
    void cloning();

    /** The repository is cloned; the AI analysis is running. */
    void analyzing();
}
