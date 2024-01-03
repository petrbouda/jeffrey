package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.repository.ProfileFile;
import pbouda.jeffrey.repository.ProfileRepository;
import pbouda.jeffrey.repository.WorkingDirProfileRepository;
import pbouda.jeffrey.service.Context;

import java.util.List;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileRepository repository;
    private final Context context;

    @Autowired
    public ProfileController(Context context) {
        this(new WorkingDirProfileRepository(), context);
    }

    public ProfileController(ProfileRepository repository, Context context) {
        this.repository = repository;
        this.context = context;
    }

    @GetMapping
    public List<ProfileFile> profiles() {
        return repository.list();
    }

    @PostMapping("/select")
    public void selectProfile(@RequestBody ProfileFile profile) {
        context.setSelectedProfile(profile);
        LOG.info("Switched profile to: {}", profile);
    }
}
