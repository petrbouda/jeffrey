package pbouda.jeffrey.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.repository.ProfileFile;
import pbouda.jeffrey.repository.ProfileRepository;
import pbouda.jeffrey.repository.WorkingDirProfileRepository;

import java.util.List;

@RestController
@RequestMapping("/profiles")
public class ProfileController {

    private final ProfileRepository repository;

    public ProfileController() {
        this(new WorkingDirProfileRepository());
    }

    public ProfileController(ProfileRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ProfileFile> profiles() {
        return repository.list();
    }
}
