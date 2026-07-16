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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.GCRootClassAggregate;
import cafe.jeffrey.profile.heapdump.model.GCRootClassLoaderAggregate;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootRetainer;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceSortBy;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakHintFinding;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

import java.util.List;

/**
 * Heap-dump object-graph browsing endpoints: histogram, class instances,
 * instance details/references, dominator tree, GC roots, GC-root paths,
 * threads and ad-hoc OQL queries.
 * <p>
 * Lifecycle endpoints live in {@link HeapDumpController}; analysis reports
 * live in {@link HeapDumpAnalysisController}.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpObjectsController {

    private final ProfileManagerResolver resolver;

    public HeapDumpObjectsController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/histogram")
    public List<ClassHistogramEntry> histogram(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "topN", defaultValue = "100") int topN,
            @RequestParam(value = "sortBy", defaultValue = "SIZE") SortBy sortBy) {
        return mgr(profileId).getClassHistogram(topN, sortBy);
    }

    @PostMapping("/query")
    public OQLQueryResult query(
            @PathVariable("profileId") String profileId,
            @RequestBody OQLQueryRequest request) {
        return mgr(profileId).executeQuery(request);
    }

    @GetMapping("/threads")
    public List<HeapThreadInfo> threads(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "includeRetained", defaultValue = "false") boolean includeRetainedSize) {
        return mgr(profileId).getThreads(includeRetainedSize);
    }

    @GetMapping("/threads/{objectId}/stack")
    public List<ThreadStackFrame> getThreadStack(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId) {
        return mgr(profileId).getThreadStack(objectId);
    }

    @GetMapping("/gc-roots")
    public GCRootSummary gcRoots(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getGCRootSummary();
    }

    @GetMapping("/gc-roots/top-retainers")
    public List<GCRootRetainer> topRetainers(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "rootKinds", required = false) List<Integer> rootKinds) {
        return mgr(profileId).getTopRetainers(limit, rootKinds == null ? List.of() : rootKinds);
    }

    @GetMapping("/gc-roots/by-class")
    public List<GCRootClassAggregate> rootsByClass(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return mgr(profileId).getRootsByClass(limit);
    }

    @GetMapping("/gc-roots/by-classloader")
    public List<GCRootClassLoaderAggregate> rootsByClassLoader(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return mgr(profileId).getRootsByClassLoader(limit);
    }

    @GetMapping("/gc-roots/leak-hints")
    public List<LeakHintFinding> leakHints(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getLeakHints();
    }

    @PostMapping("/dominator-tree/compute")
    public List<SubPhaseTiming> runComputeDominator(@PathVariable("profileId") String profileId) {
        return mgr(profileId).runComputeDominator();
    }

    @GetMapping("/dominator-tree")
    public DominatorTreeResponse getDominatorTreeRoots(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return mgr(profileId).getDominatorTreeRoots(limit);
    }

    @GetMapping("/dominator-tree/{objectId}/children")
    public DominatorTreeResponse getDominatorTreeChildren(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return mgr(profileId).getDominatorTreeChildren(objectId, offset, limit);
    }

    @GetMapping("/instance/{objectId}")
    public InstanceDetail getInstanceDetail(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "includeRetained", defaultValue = "false") boolean includeRetainedSize) {
        return mgr(profileId).getInstanceDetail(objectId, includeRetainedSize);
    }

    @GetMapping("/instance/{objectId}/referrers")
    public InstanceTreeResponse getReferrers(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        return mgr(profileId).getReferrers(objectId, limit, offset);
    }

    @GetMapping("/instance/{objectId}/reachables")
    public InstanceTreeResponse getReachables(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        return mgr(profileId).getReachables(objectId, limit, offset);
    }

    @GetMapping("/instance/{objectId}/gc-root-path")
    public List<GCRootPath> getPathToGCRoot(
            @PathVariable("profileId") String profileId,
            @PathVariable("objectId") long objectId,
            @RequestParam(value = "excludeWeak", defaultValue = "true") boolean excludeWeakRefs,
            @RequestParam(value = "maxPaths", defaultValue = "3") int maxPaths) {
        return mgr(profileId).getPathsToGCRoot(objectId, excludeWeakRefs, maxPaths);
    }

    @GetMapping("/class-instances")
    public ClassInstancesResponse getClassInstances(
            @PathVariable("profileId") String profileId,
            @RequestParam("className") String className,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "includeRetainedSize", defaultValue = "false") boolean includeRetainedSize,
            @RequestParam(value = "sortBy", defaultValue = "OBJECT_ID") InstanceSortBy sortBy) {
        return mgr(profileId).getClassInstances(className, limit, offset, includeRetainedSize, sortBy);
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }
}
