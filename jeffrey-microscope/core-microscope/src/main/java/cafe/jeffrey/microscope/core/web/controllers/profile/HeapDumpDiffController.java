/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
