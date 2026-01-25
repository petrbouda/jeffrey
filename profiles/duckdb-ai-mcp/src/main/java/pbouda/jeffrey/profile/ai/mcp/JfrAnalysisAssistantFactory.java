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

package pbouda.jeffrey.profile.ai.mcp;

import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisRequest;
import pbouda.jeffrey.profile.ai.mcp.model.JfrAnalysisResponse;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.util.function.Function;

/**
 * Factory for creating profile-specific JFR analysis assistants.
 * This factory pattern allows the ProfileManager to integrate AI analysis capabilities.
 */
public class JfrAnalysisAssistantFactory implements Function<ProfileInfo, JfrAnalysisAssistantFactory.JfrAnalysisAssistant> {

    private final JfrAnalysisAssistantService service;

    public JfrAnalysisAssistantFactory(JfrAnalysisAssistantService service) {
        this.service = service;
    }

    @Override
    public JfrAnalysisAssistant apply(ProfileInfo profileInfo) {
        return new JfrAnalysisAssistant(profileInfo, service);
    }

    /**
     * Check if the AI assistant is available.
     */
    public boolean isAvailable() {
        return service.isAvailable();
    }

    /**
     * Profile-specific JFR analysis assistant.
     * Bound to a specific profile for analysis operations.
     */
    public static class JfrAnalysisAssistant {
        private final ProfileInfo profileInfo;
        private final JfrAnalysisAssistantService service;

        public JfrAnalysisAssistant(ProfileInfo profileInfo, JfrAnalysisAssistantService service) {
            this.profileInfo = profileInfo;
            this.service = service;
        }

        /**
         * Analyze JFR events with an AI assistant.
         *
         * @param request the analysis request
         * @return the analysis response
         */
        public JfrAnalysisResponse analyze(JfrAnalysisRequest request) {
            return service.analyze(profileInfo, request);
        }

        /**
         * Quick analysis with a simple question.
         *
         * @param question the question to ask
         * @return the analysis response
         */
        public JfrAnalysisResponse ask(String question) {
            return analyze(new JfrAnalysisRequest(question));
        }

        /**
         * Check if the assistant is available.
         */
        public boolean isAvailable() {
            return service.isAvailable();
        }

        /**
         * Get the profile this assistant is bound to.
         */
        public ProfileInfo profileInfo() {
            return profileInfo;
        }
    }
}
