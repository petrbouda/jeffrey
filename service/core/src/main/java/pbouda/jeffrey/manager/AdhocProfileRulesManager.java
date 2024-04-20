package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.rules.JdkRulesResultsProvider;
import pbouda.jeffrey.rules.RulesResultsProvider;

public class AdhocProfileRulesManager implements ProfileRulesManager {

    private final RulesResultsProvider resultsProvider;
    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;

    public AdhocProfileRulesManager(ProfileInfo profileInfo, WorkingDirs workingDirs) {
        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.resultsProvider = new JdkRulesResultsProvider();
    }

    @Override
    public JsonNode ruleResults() {
        return resultsProvider.results(workingDirs.profileRecording(profileInfo));
    }
}
