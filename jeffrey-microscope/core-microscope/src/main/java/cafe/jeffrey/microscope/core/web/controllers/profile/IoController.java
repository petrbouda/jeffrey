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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/io")
public class IoController {

    private static final Logger LOG = LoggerFactory.getLogger(IoController.class);

    private final ProfileManagerResolver resolver;

    public IoController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/{kind}")
    public IoOverview overview(@PathVariable("profileId") String profileId, @PathVariable("kind") String kind) {
        LOG.debug("Fetching I/O overview: kind={}", kind);
        return mgr(profileId).overview(IoKind.fromPath(kind));
    }

    @GetMapping("/{kind}/timeline")
    public TimeseriesData timeline(@PathVariable("profileId") String profileId, @PathVariable("kind") String kind) {
        LOG.debug("Fetching I/O throughput timeline: kind={}", kind);
        return mgr(profileId).throughputTimeline(IoKind.fromPath(kind));
    }

    @GetMapping("/{kind}/slowest")
    public List<IoOperation> slowest(@PathVariable("profileId") String profileId, @PathVariable("kind") String kind) {
        LOG.debug("Fetching slowest I/O operations: kind={}", kind);
        return mgr(profileId).slowestOperations(IoKind.fromPath(kind));
    }

    @GetMapping("/{kind}/endpoints")
    public List<IoEndpoint> endpoints(@PathVariable("profileId") String profileId, @PathVariable("kind") String kind) {
        LOG.debug("Fetching I/O endpoints: kind={}", kind);
        return mgr(profileId).endpoints(IoKind.fromPath(kind));
    }

    @GetMapping("/file/directories")
    public List<IoEndpoint> directories(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching file I/O directories");
        return mgr(profileId).directories();
    }

    private IoManager mgr(String profileId) {
        return resolver.resolve(profileId).ioManager();
    }
}
