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
import cafe.jeffrey.profile.model.FlamegraphPanel;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.PanelContext;
import cafe.jeffrey.profile.resources.request.GenerateFlamegraphRequest;
import cafe.jeffrey.microscope.model.GraphType;

import java.util.List;

import static cafe.jeffrey.microscope.core.web.controllers.profile.FlamegraphController.mapToGenerateRequest;

@RestController
@RequestMapping({
        "/api/internal/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-flamegraph",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{primaryProfileId}/diff/{secondaryProfileId}/differential-flamegraph"
})
public class DifferentialFlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(DifferentialFlamegraphController.class);

    private final ProfileManagerResolver resolver;
    private final JfrFlamegraphPanelProvider panelProvider;

    public DifferentialFlamegraphController(ProfileManagerResolver resolver, JfrFlamegraphPanelProvider panelProvider) {
        this.resolver = resolver;
        this.panelProvider = panelProvider;
    }

    @PostMapping(produces = ProfileMediaTypes.PROTOBUF)
    public byte[] generate(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId,
            @RequestBody GenerateFlamegraphRequest request) {
        LOG.debug("Generating diff flamegraph: eventType={}", request.eventType());
        FlamegraphManager diffManager = diffManager(primaryProfileId, secondaryProfileId);
        ProfileManager primary = resolver.resolve(primaryProfileId);
        GraphParameters params = mapToGenerateRequest(primary, request, GraphType.DIFFERENTIAL);
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

    @GetMapping("/panels")
    public List<FlamegraphPanel> panels(
            @PathVariable("primaryProfileId") String primaryProfileId,
            @PathVariable("secondaryProfileId") String secondaryProfileId) {
        FlamegraphManager diffManager = diffManager(primaryProfileId, secondaryProfileId);
        List<FlamegraphPanel> panels = panelProvider.panels(diffManager.eventSummaries(), PanelContext.DIFFERENTIAL);
        LOG.debug("Listed diff flamegraph panels: profileId={} count={}", primaryProfileId, panels.size());
        return panels;
    }

    private FlamegraphManager diffManager(String primaryId, String secondaryId) {
        ProfileManager primary = resolver.resolve(primaryId);
        ProfileManager secondary = resolver.resolve(secondaryId);
        return primary.diffFlamegraphManager(secondary);
    }
}
