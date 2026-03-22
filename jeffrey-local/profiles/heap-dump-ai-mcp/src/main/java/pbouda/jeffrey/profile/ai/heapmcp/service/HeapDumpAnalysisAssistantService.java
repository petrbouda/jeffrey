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

package pbouda.jeffrey.profile.ai.heapmcp.service;

import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisRequest;
import pbouda.jeffrey.profile.ai.heapmcp.model.HeapDumpAnalysisResponse;
import pbouda.jeffrey.profile.ai.heapmcp.tools.HeapDumpToolsDelegate;

/**
 * Service for AI-powered heap dump analysis.
 * Provides conversational interface for analyzing Java heap dumps.
 */
public interface HeapDumpAnalysisAssistantService {

    /**
     * Check if the AI assistant is available and properly configured.
     */
    boolean isAvailable();

    /**
     * Get the name of the AI model being used.
     */
    String getModelName();

    /**
     * Get the display name of the AI provider (e.g. "Claude", "ChatGPT").
     */
    String getProviderName();

    /**
     * Analyze a heap dump with an AI assistant.
     *
     * @param delegate the heap dump tools delegate for the current profile
     * @param request  the analysis request
     * @return the analysis response
     */
    HeapDumpAnalysisResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request);
}
