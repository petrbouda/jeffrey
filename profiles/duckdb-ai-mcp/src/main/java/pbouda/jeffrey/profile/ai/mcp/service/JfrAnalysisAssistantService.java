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

package pbouda.jeffrey.profile.ai.mcp.service;

import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisRequest;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisResponse;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

/**
 * Service for AI-powered JFR analysis.
 * Provides conversational interface for analyzing JFR events stored in DuckDB.
 */
public interface JfrAnalysisAssistantService {

    /**
     * Check if the AI assistant is available and properly configured.
     *
     * @return true if the assistant is available
     */
    boolean isAvailable();

    /**
     * Get the name of the AI model being used.
     *
     * @return the model name, or null if not configured
     */
    String getModelName();

    /**
     * Analyze JFR events for a specific profile.
     *
     * @param profileInfo the profile to analyze
     * @param request     the analysis request
     * @return the analysis response
     */
    JfrAnalysisResponse analyze(ProfileInfo profileInfo, JfrAnalysisRequest request);
}
