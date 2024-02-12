package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.DeleteFlamegraphRequest;
import pbouda.jeffrey.controller.model.ExportRequest;
import pbouda.jeffrey.controller.model.FlamegraphListRequest;
import pbouda.jeffrey.controller.model.GetFlamegraphRequest;
import pbouda.jeffrey.exception.Exceptions;
import pbouda.jeffrey.manager.GraphManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.GraphContent;
import pbouda.jeffrey.repository.GraphInfo;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/flamegraph")
public class FlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(FlamegraphController.class);

    private final ProfilesManager profilesManager;

    @Autowired
    public FlamegraphController(ProfilesManager profilesManager) {
        this.profilesManager = profilesManager;
    }

    @PostMapping("/all")
    public List<GraphInfo> list(@RequestBody FlamegraphListRequest request) {
        ProfileManager profileManager = profilesManager.getProfile(request.profileId())
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return sort(profileManager.flamegraphManager().allCustom());
    }

    private static List<GraphInfo> sort(List<GraphInfo> flamegraphs) {
        return flamegraphs.stream()
                .sorted(Comparator.comparing(GraphInfo::createdAt).reversed())
                .toList();
    }

    @PostMapping("/export")
    public void export(@RequestBody ExportRequest request) {
        GraphManager graphManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        if (request.flamegraphId() != null) {
            graphManager.export(request.flamegraphId());
        } else {
            graphManager.export(request.eventType());
        }

        LOG.info("Flamegraph successfully exported: {}", request);
    }

    @PostMapping
    public ObjectNode getContent(@RequestBody GetFlamegraphRequest request) {
        GraphManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        if (request.flamegraphId() == null) {
            return manager.generateComplete(request.eventType())
                    .map(GraphContent::content)
                    .orElseThrow(Exceptions.FLAMEGRAPH_NOT_FOUND);
        } else {
            return manager.get(request.flamegraphId())
                    .map(GraphContent::content)
                    .orElseThrow(Exceptions.FLAMEGRAPH_NOT_FOUND);
        }
    }

    @PostMapping("/delete")
    public void delete(@RequestBody DeleteFlamegraphRequest request) throws IOException {
        GraphManager graphManager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        graphManager.delete(request.flamegraphId());
    }
}
