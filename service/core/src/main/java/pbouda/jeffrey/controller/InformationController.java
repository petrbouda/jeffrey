package pbouda.jeffrey.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
import pbouda.jeffrey.exception.Exceptions;
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
        ProfileManager profileManager = profilesManager.getProfile(request.profileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return profileManager
                .profileInfoManager()
                .information();
    }
}
