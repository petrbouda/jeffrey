package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.DeleteFlamegraphRequest;
import pbouda.jeffrey.controller.model.GetFlamegraphRequest;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.controller.model.ProfilesIdRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.GraphManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/flamegraph")
public class FlamegraphController {

    private final ProfilesManager profilesManager;

    @Autowired
    public FlamegraphController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/all")
    public List<GraphInfo> list(@RequestBody ProfileIdRequest request) {
        ProfileManager profileManager = profilesManager.getProfile(request.profileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return sort(profileManager.flamegraphManager().allCustom());
    }

    private static List<GraphInfo> sort(List<GraphInfo> flamegraphs) {
        return flamegraphs.stream()
                .sorted(Comparator.comparing(GraphInfo::createdAt).reversed())
                .toList();
    }

    @PostMapping("/events")
    public JsonNode events(@RequestBody ProfileIdRequest request) {
        GraphManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.supportedEvents();
    }

    @PostMapping("/events/diff")
    public JsonNode events(@RequestBody ProfilesIdRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .supportedEvents();
    }

    @PostMapping("/id")
    public GraphContent getContentById(@RequestBody GetFlamegraphRequest request) {
        GraphManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.get(request.flamegraphId())
                .orElseThrow(Exceptions.FLAMEGRAPH_NOT_FOUND);
    }

    @PostMapping("/delete")
    public void delete(@RequestBody DeleteFlamegraphRequest request) throws IOException {
        GraphManager graphManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        graphManager.delete(request.flamegraphId());
    }
}
