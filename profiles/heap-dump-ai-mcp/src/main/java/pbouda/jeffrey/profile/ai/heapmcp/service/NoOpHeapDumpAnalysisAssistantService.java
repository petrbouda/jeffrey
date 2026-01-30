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
 * No-op implementation of heap dump analysis assistant when AI is not configured.
 */
public class NoOpHeapDumpAnalysisAssistantService implements HeapDumpAnalysisAssistantService {

    private static final String NOT_CONFIGURED_MESSAGE = """
            AI-powered heap dump analysis is not configured.

            To enable AI analysis, configure the following properties:
            - jeffrey.ai.enabled=true
            - spring.ai.anthropic.api-key=your-api-key

            See the documentation for more details on configuring AI providers.
            """;

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getModelName() {
        return null;
    }

    @Override
    public HeapDumpAnalysisResponse analyze(HeapDumpToolsDelegate delegate, HeapDumpAnalysisRequest request) {
        return HeapDumpAnalysisResponse.textOnly(NOT_CONFIGURED_MESSAGE);
    }
}
