package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.controller.model.DeleteFlamegraphRequest;
import pbouda.jeffrey.controller.model.GetFlamegraphRequest;
import pbouda.jeffrey.controller.model.ProfileIdRequest;
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

    /**
     *  [{
     *      index: 0,
     *      label: 'Execution Samples (CPU)',
     *      code: 'jdk.ExecutionSample'
     *  },{ ... }]
     */
    @PostMapping("/supported")
    public JsonNode getFlamegraphInfo(@RequestBody ProfileIdRequest request) {
        GraphManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.stacktraceTypes();

//        return Json.read("""
//        [
//            {
//                "index": 0,
//                "label": "Execution Samples (CPU)",
//                "code": "jdk.ExecutionSample"
//            },
//            {
//                "index": 1,
//                "label": "Allocations",
//                "code": "jdk.ObjectAllocationInNewTLAB"
//            },
//            {
//                "index": 2,
//                "label": "Locks",
//                "code": "jdk.ThreadPark"
//            }
//        ]""");
    }

    @PostMapping("/content/id")
    public JsonNode getContentById(@RequestBody GetFlamegraphRequest request) {
        GraphManager manager = profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .orElseThrow(Exceptions.PROFILE_NOT_FOUND);

        return manager.get(request.flamegraphId())
                .map(GraphContent::content)
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
