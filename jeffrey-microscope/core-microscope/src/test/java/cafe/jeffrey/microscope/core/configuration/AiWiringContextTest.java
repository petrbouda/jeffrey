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

package cafe.jeffrey.microscope.core.configuration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.SettingsDrivenAiChatBackend;
import cafe.jeffrey.profile.ai.claudecode.config.ClaudeCodeConfiguration;
import cafe.jeffrey.profile.ai.config.AiChatModelConfiguration;
import cafe.jeffrey.profile.ai.duckdb.heapdump.config.HeapDumpMcpConfiguration;
import cafe.jeffrey.profile.ai.duckdb.heapdump.service.HeapDumpAnalysisAssistantService;
import cafe.jeffrey.profile.ai.duckdb.jfr.config.DuckDbMcpConfiguration;
import cafe.jeffrey.profile.ai.duckdb.jfr.service.JfrAnalysisAssistantService;
import cafe.jeffrey.profile.ai.oql.config.AiAssistantConfiguration;
import cafe.jeffrey.profile.ai.oql.service.OqlAssistantService;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring that used to be decided by {@code @ConditionalOnExpression}.
 * <p>
 * Two failure modes are specific to removing those conditions, and neither shows up in a unit test:
 * the two provider modules each declared an {@code aiChatBackend} bean and were kept apart only by
 * being mutually exclusive, and the assistant services used to exist only when AI was configured. So
 * the context is built for real, once per provider.
 */
class AiWiringContextTest {

    private static final Map<String, String> DEFAULTS = Map.of(
            MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE,
            MicroscopeSettingKeys.AI_MODEL, "claude-opus-4-8",
            MicroscopeSettingKeys.AI_MAX_TOKENS, "4096",
            MicroscopeSettingKeys.AI_API_KEY, "sk-test-key",
            MicroscopeSettingKeys.AI_BASE_URL, "http://localhost:11434",
            MicroscopeSettingKeys.AI_CLI_PATH, "claude",
            MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, "120",
            MicroscopeSettingKeys.AI_MCP_URL, "http://127.0.0.1:8080/api/internal/mcp/claude-code");

    /**
     * The OpenAI SDK infers which service to talk to from the model name, so each provider gets a name
     * it would realistically be configured with.
     */
    private static final Map<String, String> MODELS = Map.of(
            "claude", "claude-opus-4-8",
            "chatgpt", "gpt-5.5",
            "ollama", "llama4",
            "claude-code", "claude-opus-4-8");

    /** Supplies only what the AI configurations need; nothing here touches a database or the network. */
    @Configuration
    @Import({
            AiChatModelConfiguration.class,
            ClaudeCodeConfiguration.class,
            AiAssistantConfiguration.class,
            DuckDbMcpConfiguration.class,
            HeapDumpMcpConfiguration.class})
    static class AiWiringTestConfiguration {

        static final ThreadLocal<String> PROVIDER = ThreadLocal.withInitial(() -> "none");

        @Bean
        public SettingsStore settingsStore() {
            String provider = PROVIDER.get();
            return new SettingsStore(DEFAULTS, Map.of(
                    MicroscopeSettingKeys.AI_PROVIDER, provider,
                    MicroscopeSettingKeys.AI_MODEL, MODELS.getOrDefault(provider, "")));
        }

        @Bean
        public DatabaseManagerResolver databaseManagerResolver() {
            return Mockito.mock(DatabaseManagerResolver.class);
        }
    }

    private static AnnotationConfigApplicationContext contextFor(String provider) {
        AiWiringTestConfiguration.PROVIDER.set(provider);
        try {
            return new AnnotationConfigApplicationContext(AiWiringTestConfiguration.class);
        } finally {
            AiWiringTestConfiguration.PROVIDER.remove();
        }
    }

    @Nested
    class ContextStarts {

        @ParameterizedTest
        @ValueSource(strings = {"none", "claude", "chatgpt", "ollama", "claude-code"})
        void startsForEveryProvider(String provider) {
            try (AnnotationConfigApplicationContext context = contextFor(provider)) {
                assertNotNull(context.getBean(AiChatBackend.class));
            }
        }

        /**
         * The Spring AI and Claude Code modules each used to declare their own {@code aiChatBackend}.
         * With bean overriding disabled, a second definition fails the context outright.
         */
        @ParameterizedTest
        @ValueSource(strings = {"none", "claude", "claude-code"})
        void declaresExactlyOneChatBackend(String provider) {
            try (AnnotationConfigApplicationContext context = contextFor(provider)) {
                assertEquals(1, context.getBeanNamesForType(AiChatBackend.class).length);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"none", "claude-code"})
        void theBackendIsTheSettingsDrivenOne(String provider) {
            try (AnnotationConfigApplicationContext context = contextFor(provider)) {
                assertNotNull(context.getBean(SettingsDrivenAiChatBackend.class));
                assertSame(context.getBean(AiChatBackend.class), context.getBean(SettingsDrivenAiChatBackend.class));
            }
        }

        @Test
        void noBackgroundMachineryIsRegistered() {
            try (AnnotationConfigApplicationContext context = contextFor("none")) {
                assertEquals(0, context.getBeanNamesForType(ExecutorService.class).length);
            }
        }
    }

    @Nested
    class AssistantsAlwaysExist {

        /**
         * The assistants used to be swapped for No-Op implementations when AI was off, which fixed the
         * choice at startup. They are now always the real service and answer from the backend.
         */
        @ParameterizedTest
        @ValueSource(strings = {"none", "claude-code"})
        void everyAssistantIsRegistered(String provider) {
            try (AnnotationConfigApplicationContext context = contextFor(provider)) {
                assertNotNull(context.getBean(JfrAnalysisAssistantService.class));
                assertNotNull(context.getBean(HeapDumpAnalysisAssistantService.class));
                assertNotNull(context.getBean(OqlAssistantService.class));
                assertNotNull(context.getBean(McpToolsetFactory.class));
            }
        }

        @Test
        void assistantsReportUnavailableWhenNoProviderIsConfigured() {
            try (AnnotationConfigApplicationContext context = contextFor("none")) {
                assertFalse(context.getBean(JfrAnalysisAssistantService.class).isAvailable());
                assertFalse(context.getBean(HeapDumpAnalysisAssistantService.class).isAvailable());
                assertFalse(context.getBean(OqlAssistantService.class).isAvailable());
            }
        }

        @Test
        void oqlStatusKeepsItsDisabledPayloadWhenNoProviderIsConfigured() {
            try (AnnotationConfigApplicationContext context = contextFor("none")) {
                var status = context.getBean(OqlAssistantService.class).getStatus();

                assertFalse(status.enabled());
                assertEquals("none", status.provider());
                assertFalse(status.configured());
            }
        }

        @Test
        void jfrStatusReportsNullProviderWhenNoProviderIsConfigured() {
            try (AnnotationConfigApplicationContext context = contextFor("none")) {
                JfrAnalysisAssistantService service = context.getBean(JfrAnalysisAssistantService.class);

                assertEquals(null, service.getProviderName());
                assertEquals(null, service.getModelName());
            }
        }
    }

    @Nested
    class ProviderSwitchAtRuntime {

        /**
         * The point of the whole change: going from no AI to a configured provider without rebuilding
         * the context, and having the assistants pick it up because they share the same backend object.
         */
        @Test
        void enablingAiMakesTheAssistantsAvailableWithoutRebuildingTheContext() {
            try (AnnotationConfigApplicationContext context = contextFor("none")) {
                JfrAnalysisAssistantService assistant = context.getBean(JfrAnalysisAssistantService.class);
                assertFalse(assistant.isAvailable());

                context.getBean(SettingsStore.class).put(MicroscopeSettingKeys.AI_PROVIDER, "ollama");

                assertTrue(assistant.isAvailable());
                assertEquals("Ollama", assistant.getProviderName());
            }
        }

        @Test
        void disablingAiMakesTheAssistantsUnavailableAgain() {
            try (AnnotationConfigApplicationContext context = contextFor("ollama")) {
                JfrAnalysisAssistantService assistant = context.getBean(JfrAnalysisAssistantService.class);
                assertTrue(assistant.isAvailable());

                context.getBean(SettingsStore.class)
                        .put(MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE);

                assertFalse(assistant.isAvailable());
            }
        }

        @Test
        void switchingBetweenProvidersIsReflectedByTheAssistants() {
            try (AnnotationConfigApplicationContext context = contextFor("ollama")) {
                OqlAssistantService assistant = context.getBean(OqlAssistantService.class);
                assertEquals("Ollama", assistant.getStatus().provider());

                context.getBean(SettingsStore.class).put(MicroscopeSettingKeys.AI_PROVIDER, "claude");

                assertEquals("Claude", assistant.getStatus().provider());
            }
        }
    }
}
