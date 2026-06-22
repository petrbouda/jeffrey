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
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.chat.ChatMessage;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.service.HeapDumpAnalysisAssistantService;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap/ai-analysis")
public class HeapDumpAiAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpAiAnalysisController.class);

    private final ProfileManagerResolver resolver;
    private final HeapDumpAnalysisAssistantService assistantService;

    public HeapDumpAiAnalysisController(
            ProfileManagerResolver resolver,
            HeapDumpAnalysisAssistantService assistantService) {
        this.resolver = resolver;
        this.assistantService = assistantService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        LOG.debug("Checking heap dump AI analysis status");
        return new StatusResponse(
                assistantService.isAvailable(),
                assistantService.getProviderName(),
                assistantService.getModelName());
    }

    @PostMapping("/chat")
    public AssistantResponse chat(
            @PathVariable("profileId") String profileId,
            @RequestBody ChatRequest request) {
        LOG.debug("Heap dump AI analysis chat request");
        HeapDumpAnalysisRequest analysisRequest = new HeapDumpAnalysisRequest(
                profileId,
                request.message(),
                request.history() != null ? request.history() : List.of());
        HeapDumpToolsDelegate delegate = new HeapDumpManagerToolsDelegate(resolver.resolve(profileId).heapDumpManager());
        return assistantService.analyze(delegate, analysisRequest);
    }

    public record StatusResponse(boolean available, String provider, String model) {
    }

    public record ChatRequest(String message, List<ChatMessage> history) {
    }
}
