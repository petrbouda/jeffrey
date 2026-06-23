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

package cafe.jeffrey.performance.analyst.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingAiPromptManager;
import cafe.jeffrey.performance.analyst.mcp.RepoToolsRegistry;
import cafe.jeffrey.performance.analyst.mcp.RepoToolsetFactory;
import cafe.jeffrey.performance.analyst.persistence.GeneratedRecommendationRepository;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTaskRegistry;
import cafe.jeffrey.performance.analyst.recommendations.RecordingRecommendationManager;
import cafe.jeffrey.performance.analyst.recommendations.RepositoryCloner;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Wiring for repository-aware AI recommendations. Active only when an AI provider is configured (same
 * guard as {@link PerformanceAnalystAiConfiguration}), since the manager needs the {@link ChatClient}.
 * When AI is disabled these beans — and the gated {@code RecordingRecommendationController} — are
 * absent, so the feature simply does not exist and the app starts normally.
 */
@Configuration
@ConditionalOnExpression(RecommendationConfiguration.AI_ENABLED)
public class RecommendationConfiguration {

    static final String AI_ENABLED = "'${jeffrey.performance-analyst.ai.provider:none}' != 'none'";

    private static final String HOME_DIR =
            "${jeffrey.performance-analyst.home.dir:${user.home}/.jeffrey-performance-analyst}";
    private static final String TEMP_SUBDIR = "temp";

    private static final String MCP_URL =
            "${jeffrey.performance-analyst.ai.mcp-url:http://127.0.0.1:8080/api/internal/mcp/claude-code}";

    @Bean
    public RepositoryCloner repositoryCloner(@Value(HOME_DIR) String homeDir) {
        Path tempBase = Path.of(homeDir).resolve(TEMP_SUBDIR);
        try {
            Files.createDirectories(tempBase);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create temp directory: " + tempBase, e);
        }
        return new RepositoryCloner(TempDirFactory.of(tempBase));
    }

    @Bean
    public RecommendationTaskRegistry recommendationTaskRegistry(Clock clock) {
        return new RecommendationTaskRegistry(clock);
    }

    @Bean
    public RepoToolsRegistry repoToolsRegistry() {
        return new RepoToolsRegistry();
    }

    @Bean
    public RepoToolsetFactory repoToolsetFactory(@Value(MCP_URL) String mcpUrl) {
        return new RepoToolsetFactory(mcpUrl);
    }

    @Bean
    public RecordingRecommendationManager recordingRecommendationManager(
            VersionControlSystemManager versionControlSystemManager,
            RecordingAiPromptManager recordingAiPromptManager,
            RepositoryCloner repositoryCloner,
            AiChatBackend aiChatBackend,
            RepoToolsRegistry repoToolsRegistry,
            RepoToolsetFactory repoToolsetFactory,
            GeneratedRecommendationRepository generatedRecommendationRepository,
            Clock clock) {
        return new RecordingRecommendationManager(
                versionControlSystemManager, recordingAiPromptManager, repositoryCloner, aiChatBackend,
                repoToolsRegistry, repoToolsetFactory, generatedRecommendationRepository, clock);
    }
}
