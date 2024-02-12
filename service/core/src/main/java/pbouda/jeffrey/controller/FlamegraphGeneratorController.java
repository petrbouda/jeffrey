package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.controller.model.GenerateByEventTypeRequest;
import pbouda.jeffrey.controller.model.GenerateStartupDiffRequest;
import pbouda.jeffrey.controller.model.GenerateWithRangeRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.GraphManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.GraphContent;

@RestController
@RequestMapping("/flamegraph/generate")
public class FlamegraphGeneratorController {

    private final ProfilesManager profilesManager;

    @Autowired
    public FlamegraphGeneratorController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public ObjectNode generate(@RequestBody GenerateByEventTypeRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return graphManager.generateComplete(request.eventType())
                .map(GraphContent::content)
                .orElseThrow(Exceptions.serverError("Cannot generate a flamegraph"));
    }

    @PostMapping("/range")
    public ObjectNode generateRange(@RequestBody GenerateWithRangeRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.profileId()).
                map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return graphManager.generateCustom(request.eventType(), request.timeRange(), request.name())
                .map(GraphContent::content)
                .orElseThrow(Exceptions.serverError("Cannot generate a flamegraph"));
    }

    @PostMapping("/diff")
    public ObjectNode getStartupDiff(@RequestBody GenerateStartupDiffRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .generateCustom(request.eventType(), request.timeRange(), request.name())
                .map(GraphContent::content)
                .orElseThrow(Exceptions.serverError("Cannot generate a flamegraph"));
    }
}
