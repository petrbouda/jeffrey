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

package pbouda.jeffrey.profile.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import pbouda.jeffrey.profile.ai.prompt.OqlSystemPrompt;
import pbouda.jeffrey.profile.ai.service.*;

public class AiAssistantConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AiAssistantConfiguration.class);

    /**
     * Create the OQL Assistant Service when AI is enabled and a ChatModel is available.
     * Spring AI configuration creates the ChatModel bean when an API key is configured.
     */
    @Bean
    @ConditionalOnProperty(name = "jeffrey.ai.enabled", havingValue = "true")
    public OqlAssistantService oqlAssistantService(
            ChatModel chatModel,
            @Value("${jeffrey.ai.enabled:false}") boolean enabled,
            @Value("${jeffrey.ai.provider:none}") String provider) {
        LOG.info("Creating OQL Assistant Service: provider={}", provider);
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(OqlSystemPrompt.SYSTEM_PROMPT)
                .build();
        return new OqlAssistantServiceImpl(chatClient, new AiAssistantConfig(enabled, provider));
    }

    /**
     * Create a no-op service when AI is not configured or no ChatModel is available.
     */
    @Bean
    @ConditionalOnMissingBean(OqlAssistantService.class)
    public OqlAssistantService noOpOqlAssistantService() {
        LOG.info("Creating No-Op OQL Assistant Service (AI not configured)");
        return new NoOpOqlAssistantService();
    }

    /**
     * Create the heap dump context extractor.
     */
    @Bean
    public HeapDumpContextExtractor heapDumpContextExtractor() {
        return new HeapDumpContextExtractor();
    }
}
