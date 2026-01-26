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
 * No-op implementation of JFR Analysis Assistant when AI is not configured.
 */
public class NoOpJfrAnalysisAssistantService implements JfrAnalysisAssistantService {

    private static final String NOT_CONFIGURED_MESSAGE = """
            AI-powered JFR analysis is not configured.

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
    public JfrAnalysisResponse analyze(ProfileInfo profileInfo, JfrAnalysisRequest request) {
        return JfrAnalysisResponse.textOnly(NOT_CONFIGURED_MESSAGE);
    }
}
