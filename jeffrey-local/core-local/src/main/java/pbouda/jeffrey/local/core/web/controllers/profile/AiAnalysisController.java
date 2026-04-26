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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisRequest;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisResponse;
import pbouda.jeffrey.profile.ai.mcp.model.JfrChatMessage;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;

import java.util.List;

@RestController
@RequestMapping({
        "/api/internal/profiles/{profileId}/ai-analysis",
        "/api/internal/quick-analysis/profiles/{profileId}/ai-analysis",
        "/api/internal/workspaces/{workspaceId}/projects/{projectId}/profiles/{profileId}/ai-analysis"
})
public class AiAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(AiAnalysisController.class);

    private final ProfileManagerResolver resolver;
    private final JfrAnalysisAssistantService assistantService;

    public AiAnalysisController(
            ProfileManagerResolver resolver,
            JfrAnalysisAssistantService assistantService) {
        this.resolver = resolver;
        this.assistantService = assistantService;
    }

    @GetMapping("/status")
    public AiAnalysisStatusResponse status() {
        LOG.debug("Checking AI analysis status");
        return new AiAnalysisStatusResponse(
                assistantService.isAvailable(),
                assistantService.getProviderName(),
                assistantService.getModelName());
    }

    @PostMapping("/chat")
    public JfrAnalysisResponse chat(
            @PathVariable("profileId") String profileId,
            @RequestBody AiAnalysisChatRequest request) {
        LOG.debug("AI analysis chat request");
        JfrAnalysisRequest analysisRequest = new JfrAnalysisRequest(
                request.message(),
                request.history() != null ? request.history() : List.of(),
                request.canModify() != null && request.canModify());
        return assistantService.analyze(resolver.resolve(profileId).info(), analysisRequest);
    }

    public record AiAnalysisStatusResponse(boolean available, String provider, String model) {
    }

    public record AiAnalysisChatRequest(
            String message,
            List<JfrChatMessage> history,
            Boolean canModify) {
    }
}
