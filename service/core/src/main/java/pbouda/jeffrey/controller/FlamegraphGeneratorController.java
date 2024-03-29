package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GenerateByEventTypeRequest;
import pbouda.jeffrey.controller.model.GenerateDiffRequest;
import pbouda.jeffrey.controller.model.GenerateWithRangeRequest;
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

    @PostMapping("/complete")
    public ObjectNode generate(@RequestBody GenerateByEventTypeRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return graphManager.generate(request.eventType());
    }

    @PostMapping("/range")
    public ObjectNode generateRange(@RequestBody GenerateWithRangeRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.primaryProfileId()).
                map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return graphManager.generate(request.eventType(), request.timeRange());
    }

    @PostMapping("/diff/complete")
    public ObjectNode generateDiff(@RequestBody GenerateDiffRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .generate(request.eventType());
    }

    @PostMapping("/diff/range")
    public ObjectNode generateDiffRange(@RequestBody GenerateDiffRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .generate(request.eventType(), request.timeRange());
    }
}
