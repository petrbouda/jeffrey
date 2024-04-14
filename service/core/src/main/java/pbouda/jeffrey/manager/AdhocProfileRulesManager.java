package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.rules.JdkRulesResultsProvider;
import pbouda.jeffrey.rules.RulesResultsProvider;

public class AdhocProfileRulesManager implements ProfileRulesManager {

    private final RulesResultsProvider resultsProvider;
    private final ProfileInfo profileInfo;

    public AdhocProfileRulesManager(ProfileInfo profileInfo) {
        this.profileInfo = profileInfo;
        this.resultsProvider = new JdkRulesResultsProvider();
    }

    @Override
    public JsonNode ruleResults() {
        return resultsProvider.results(profileInfo.recordingPath());
    }
}
