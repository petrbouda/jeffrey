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

package pbouda.jeffrey.profile.ai.mcp.model;

import java.util.List;

/**
 * Response from JFR analysis assistant.
 *
 * @param content     the analysis response text
 * @param suggestions optional follow-up suggestions for the user
 * @param toolsUsed   list of MCP tools that were invoked during analysis
 */
public record JfrAnalysisResponse(
        String content,
        List<String> suggestions,
        List<String> toolsUsed
) {
    public static JfrAnalysisResponse textOnly(String content) {
        return new JfrAnalysisResponse(content, List.of(), List.of());
    }

    public static JfrAnalysisResponse error(String errorMessage) {
        return new JfrAnalysisResponse(errorMessage, List.of(), List.of());
    }
}
