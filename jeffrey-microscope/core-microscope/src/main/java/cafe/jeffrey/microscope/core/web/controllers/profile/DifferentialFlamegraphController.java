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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.resources.request.GenerateFlamegraphRequest;
import cafe.jeffrey.shared.common.GraphType;

import java.util.List;

import static cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.PROTOBUF_MEDIA_TYPE;
import static cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.mapToGenerateRequest;

@RestController
@RequestMapping({
        "/api/internal/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-flamegraph",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-flamegraph"
})
public class DifferentialFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(DifferentialFlamegraphController.class);

    private final ProfileManagerResolver resolver;

    public DifferentialFlamegraphController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping(produces = PROTOBUF_MEDIA_TYPE)
    public byte[] generate(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId,
            @RequestBody GenerateFlamegraphRequest request) {
        LOG.debug("Generating diff flamegraph: eventType={}", request.eventType());
        FlamegraphManager diffManager = diffManager(primaryProfileId, secondaryProfileId);
        ProfileManager primary = resolver.resolve(primaryProfileId);
        GraphParameters params = mapToGenerateRequest(primary.info(), request, GraphType.DIFFERENTIAL);
        return diffManager.generate(params);
    }

    @GetMapping("/events")
    public List<EventSummaryResult> events(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId) {
        FlamegraphManager diffManager = diffManager(primaryProfileId, secondaryProfileId);
        var result = diffManager.eventSummaries();
        LOG.debug("Listed diff flamegraph event types: profileId={} count={}", primaryProfileId, result.size());
        return result;
    }

    private FlamegraphManager diffManager(String primaryId, String secondaryId) {
        ProfileManager primary = resolver.resolve(primaryId);
        ProfileManager secondary = resolver.resolve(secondaryId);
        return primary.diffFlamegraphManager(secondary);
    }
}
