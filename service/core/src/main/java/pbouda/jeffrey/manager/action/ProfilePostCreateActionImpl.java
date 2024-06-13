package pbouda.jeffrey.manager.action;

import pbouda.jeffrey.manager.ProfileManager;

public class ProfilePostCreateActionImpl implements ProfilePostCreateAction {

    @Override
    public void execute(ProfileManager profileManager) {
        // Create and cache Information
        profileManager.profileInfoManager()
                .information();

        // Create and cache AutoAnalysis
        profileManager.profileAutoAnalysisManager()
                .ruleResults();
    }
}
