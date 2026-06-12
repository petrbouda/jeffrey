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

package cafe.jeffrey.profile.ai.duckdb.heapdump.service;

import cafe.jeffrey.profile.ai.chat.AiAssistantService;
import cafe.jeffrey.profile.ai.chat.AssistantResponse;
import cafe.jeffrey.profile.ai.duckdb.heapdump.model.HeapDumpAnalysisRequest;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpToolsDelegate;

/**
 * Service for AI-powered heap dump analysis.
 * Provides conversational interface for analyzing Java heap dumps.
 */
public interface HeapDumpAnalysisAssistantService extends AiAssistantService {

    /**
     * Analyze a heap dump with an AI assistant.
     *
     * @param delegate the heap dump tools delegate for the current profile
     * @param request  the analysis request
     * @return the analysis response
     */
    AssistantResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request);
}
