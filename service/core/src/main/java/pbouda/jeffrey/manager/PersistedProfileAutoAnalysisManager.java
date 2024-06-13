package pbouda.jeffrey.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.rules.AnalysisItem;
import pbouda.jeffrey.rules.JdkRulesResultsProvider;
import pbouda.jeffrey.rules.RulesResultsProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class PersistedProfileAutoAnalysisManager implements ProfileAutoAnalysisManager {

    private static final TypeReference<List<AnalysisItem>> JSON_TYPE = new TypeReference<List<AnalysisItem>>() {
    };

    private final Path recordingFile;
    private final CacheRepository cacheRepository;
    private final RulesResultsProvider resultsProvider;

    public PersistedProfileAutoAnalysisManager(Path recordingFile, CacheRepository cacheRepository) {
        this.recordingFile = recordingFile;
        this.cacheRepository = cacheRepository;
        this.resultsProvider = new JdkRulesResultsProvider();
    }

    @Override
    public List<AnalysisItem> ruleResults() {
        Optional<JsonNode> valueOpt = cacheRepository.get(CacheKey.RULES);
        if (valueOpt.isPresent()) {
            try {
                return Json.mapper().treeToValue(valueOpt.get(), JSON_TYPE);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            List<AnalysisItem> results = resultsProvider.results(recordingFile);
            JsonNode json = Json.mapper().valueToTree(results);
            cacheRepository.insert(CacheKey.RULES, json);
            return results;
        }
    }
}
