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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.fleet.FleetPatternsManager;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.persistence.RecommendationClaimRepository;
import cafe.jeffrey.performance.analyst.recommendations.TopRecommendationsManager;

/**
 * Wiring for the global Overview's "Highest Impact" list and the fleet-wide pattern rollup. Always
 * active (unlike {@link RecommendationConfiguration}) because both only read already-stored results, so
 * the Overview works regardless of whether an AI provider is configured.
 */
@Configuration
public class OverviewConfiguration {

    @Bean
    public TopRecommendationsManager topRecommendationsManager(
            GeneratedRecommendationRepository generatedRecommendationRepository) {
        return new TopRecommendationsManager(generatedRecommendationRepository);
    }

    @Bean
    public FleetPatternsManager fleetPatternsManager(RecommendationClaimRepository recommendationClaimRepository) {
        return new FleetPatternsManager(recommendationClaimRepository);
    }
}
