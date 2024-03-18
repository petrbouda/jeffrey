package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GenerateTimeseriesRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.GraphManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;

@RestController
@RequestMapping("/timeseries")
public class TimeseriesController {

    private final ProfilesManager profilesManager;

    public TimeseriesController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/generate/complete")
    public ArrayNode generate(@RequestBody GenerateTimeseriesRequest request) {
        GraphManager timeseriesManager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return timeseriesManager.timeseries(request.eventType());
    }

    @PostMapping("/generate/diff")
    public ArrayNode generateDiff(@RequestBody GenerateTimeseriesRequest request) {
        ProfileManager primaryManager = profilesManager.getProfile(request.primaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);
        ProfileManager secondaryManager = profilesManager.getProfile(request.secondaryProfileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return primaryManager.diffgraphManager(secondaryManager)
                .timeseries(request.eventType());
    }
}
