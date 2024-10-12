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

package pbouda.jeffrey.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Recording;
import pbouda.jeffrey.common.analysis.AutoAnalysisResult;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.rules.JdkRulesResultsProvider;
import pbouda.jeffrey.rules.RulesResultsProvider;

import java.util.List;
import java.util.Optional;

public class PersistedAutoAnalysisManager implements AutoAnalysisManager {

    private static final TypeReference<List<AutoAnalysisResult>> JSON_TYPE = new TypeReference<List<AutoAnalysisResult>>() {
    };

    private final List<Recording> recordings;
    private final CacheRepository cacheRepository;
    private final RulesResultsProvider resultsProvider;

    public PersistedAutoAnalysisManager(List<Recording> recordings, CacheRepository cacheRepository) {
        this.recordings = recordings;
        this.cacheRepository = cacheRepository;
        this.resultsProvider = new JdkRulesResultsProvider();
    }

    @Override
    public List<AutoAnalysisResult> ruleResults() {
        Optional<JsonNode> valueOpt = cacheRepository.get(CacheKey.RULES);
        if (valueOpt.isPresent()) {
            try {
                return Json.mapper().treeToValue(valueOpt.get(), JSON_TYPE);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            List<AutoAnalysisResult> results = resultsProvider.results(recordings);
            JsonNode json = Json.mapper().valueToTree(results);
            cacheRepository.insert(CacheKey.RULES, json);
            return results;
        }
    }
}
