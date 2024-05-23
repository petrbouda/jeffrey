package pbouda.jeffrey.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
