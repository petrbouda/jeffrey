package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GenerateDiffFlamegraphRequest;
import pbouda.jeffrey.controller.model.GenerateFlamegraphRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.GraphManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;

@RestController
@RequestMapping("/flamegraph/generate")
public class FlamegraphGeneratorController {

    private final ProfilesManager profilesManager;

    @Autowired
    public FlamegraphGeneratorController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public ObjectNode generate(@RequestBody GenerateFlamegraphRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.primaryProfileId()).
                map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return graphManager.generate(request.eventType(), request.timeRange(), request.threadMode());
    }

    @PostMapping("/diff")
    public ObjectNode generateDiffRange(@RequestBody GenerateDiffFlamegraphRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .generate(request.eventType(), request.timeRange(), false);
    }
}
