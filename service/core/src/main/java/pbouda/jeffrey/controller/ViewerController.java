package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GenerateAllEventTypesRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.EventViewerManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;

@RestController
@RequestMapping("/viewer")
public class ViewerController {

    private final ProfilesManager profilesManager;

    public ViewerController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/all")
    public JsonNode generate(@RequestBody GenerateAllEventTypesRequest request) {
        EventViewerManager eventViewerManager = profilesManager.getProfile(request.primaryProfileId())
                .map(ProfileManager::eventViewerManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return eventViewerManager.allEventTypes();
    }
}
