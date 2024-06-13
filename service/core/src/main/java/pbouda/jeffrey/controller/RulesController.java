package pbouda.jeffrey.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.rules.AnalysisItem;

import java.util.List;

@RestController
@RequestMapping("/rules")
public class RulesController {
    private final ProfilesManager profilesManager;

    @Autowired
    public RulesController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public List<AnalysisItem> list(@RequestBody ProfileIdRequest request) {
        ProfileManager profileManager = profilesManager.getProfile(request.profileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return profileManager
                .profileAutoAnalysisManager()
                .ruleResults();
    }
}
