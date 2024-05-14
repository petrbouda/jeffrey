package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.ProfileInfoManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;

@RestController
@RequestMapping("/information")
public class InformationController {
    private final ProfilesManager profilesManager;

    @Autowired
    public InformationController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public byte[] list(@RequestBody ProfileIdRequest request) {
        ProfileInfoManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::profileInfoManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.information();
    }

    @PostMapping("/events")
    public JsonNode getFlamegraphInfo(@RequestBody ProfileIdRequest request) {
        ProfileInfoManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::profileInfoManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.events();
    }
}
