/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.HeapDumpDiffReport;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpDiffService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

/**
 * Heap-dump comparison between two profiles: the primary (current) dump
 * against a baseline dump, following the {@code /diff/{secondaryProfileId}}
 * convention established by the differential flamegraph.
 */
@RestController
@RequestMapping({
        "/api/internal/profiles/{primaryProfileId}/diff/{secondaryProfileId}/heap",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{primaryProfileId}/diff/{secondaryProfileId}/heap"
})
public class HeapDumpDiffController {

    private final ProfileManagerResolver resolver;

    public HeapDumpDiffController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/histogram")
    public HeapDumpDiffReport histogram(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId,
            @RequestParam(value = "topN", defaultValue = "500") int topN) {
        HeapDumpManager primary = resolver.resolve(primaryProfileId).heapDumpManager();
        HeapDumpManager baseline = resolver.resolve(secondaryProfileId).heapDumpManager();
        return HeapDumpDiffService.diff(primary, baseline, topN);
    }
}
