package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GetTimeseriesRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.TimeseriesManager;

@RestController
@RequestMapping("/timeseries")
public class TimeseriesController {

    private final ProfilesManager profilesManager;

    public TimeseriesController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/generate")
    public ArrayNode generate(@RequestBody GetTimeseriesRequest request) {
        TimeseriesManager timeseriesManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::timeseriesManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return timeseriesManager.contentByEventType(request.eventType());
    }
}
