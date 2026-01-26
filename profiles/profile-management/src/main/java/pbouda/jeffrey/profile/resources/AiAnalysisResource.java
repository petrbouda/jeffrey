/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisRequest;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisResponse;
import pbouda.jeffrey.profile.ai.mcp.model.JfrChatMessage;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;

/**
 * REST resource for AI-powered JFR analysis.
 * Provides endpoints for chat-based analysis of JFR events using DuckDB MCP tools.
 */
public class AiAnalysisResource {

    private final ProfileInfo profileInfo;
    private final JfrAnalysisAssistantService assistantService;

    public AiAnalysisResource(
            ProfileInfo profileInfo,
            JfrAnalysisAssistantService assistantService) {
        this.profileInfo = profileInfo;
        this.assistantService = assistantService;
    }

    /**
     * Get the current status of the AI analysis assistant.
     *
     * @return status information including availability
     */
    @GET
    @Path("/status")
    public AiAnalysisStatusResponse status() {
        return new AiAnalysisStatusResponse(
                assistantService.isAvailable(),
                assistantService.isAvailable() ? "anthropic" : null,
                assistantService.getModelName()
        );
    }

    /**
     * Send a chat message and get an analysis response.
     *
     * @param request the chat request with message and history
     * @return the AI response with analysis results and suggestions
     */
    @POST
    @Path("/chat")
    public JfrAnalysisResponse chat(AiAnalysisChatRequest request) {
        JfrAnalysisRequest analysisRequest = new JfrAnalysisRequest(
                request.message(),
                request.history() != null ? request.history() : List.of(),
                request.canModify() != null && request.canModify()
        );
        return assistantService.analyze(profileInfo, analysisRequest);
    }

    /**
     * Status response for the AI analysis assistant.
     */
    public record AiAnalysisStatusResponse(
            boolean available,
            String provider,
            String model
    ) {
    }

    /**
     * Chat request for AI analysis.
     *
     * @param message the user's message
     * @param history conversation history
     * @param canModify whether data modifications are allowed (must be explicitly enabled)
     */
    public record AiAnalysisChatRequest(
            String message,
            List<JfrChatMessage> history,
            Boolean canModify
    ) {
    }
}
