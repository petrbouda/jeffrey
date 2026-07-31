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
import cafe.jeffrey.performance.analyst.persistence.RecommendationClaimRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.performance.analyst.recommendations.CommitRefResolver;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationRunner;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTaskRegistry;
import cafe.jeffrey.performance.analyst.recommendations.RecordingRecommendationManager;
import cafe.jeffrey.performance.analyst.recommendations.RepositoryCloner;
import cafe.jeffrey.performance.analyst.verification.CommandRunner;
import cafe.jeffrey.performance.analyst.verification.PatchVerificationSettings;
import cafe.jeffrey.performance.analyst.verification.PatchVerifier;
import cafe.jeffrey.performance.analyst.versioncontrolsystem.VersionControlSystemManager;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

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

    private static final String MAX_CONCURRENT_RUNS =
            "${jeffrey.performance-analyst.recommendations.max-concurrent-runs:2}";
    private static final String GIT_PATH =
            "${jeffrey.performance-analyst.verification.git-path:git}";
    private static final String COMPILE_COMMAND =
            "${jeffrey.performance-analyst.verification.compile-command:}";
    private static final String TEST_COMMAND =
            "${jeffrey.performance-analyst.verification.test-command:}";
    private static final String VERIFICATION_TIMEOUT_SECONDS =
            "${jeffrey.performance-analyst.verification.timeout-seconds:600}";

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
    public CommitRefResolver commitRefResolver(RecordingTagsRepository recordingTagsRepository) {
        return new CommitRefResolver(recordingTagsRepository);
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
    public CommandRunner commandRunner() {
        return new CommandRunner();
    }

    /**
     * Patch verification runs the apply check out of the box; compiling and testing a cloned repository
     * means running that repository's build, so both stay unconfigured until an operator opts in.
     */
    @Bean
    public PatchVerifier patchVerifier(
            CommandRunner commandRunner,
            @Value(GIT_PATH) String gitPath,
            @Value(COMPILE_COMMAND) String compileCommand,
            @Value(TEST_COMMAND) String testCommand,
            @Value(VERIFICATION_TIMEOUT_SECONDS) long verificationTimeoutSeconds) {

        PatchVerificationSettings settings = new PatchVerificationSettings(
                gitPath,
                splitCommand(compileCommand),
                splitCommand(testCommand),
                Duration.ofSeconds(verificationTimeoutSeconds));
        return new PatchVerifier(commandRunner, settings);
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
            RecommendationClaimRepository recommendationClaimRepository,
            PatchVerifier patchVerifier,
            Clock clock) {
        return new RecordingRecommendationManager(
                versionControlSystemManager, recordingAiPromptManager, repositoryCloner, aiChatBackend,
                repoToolsRegistry, repoToolsetFactory, generatedRecommendationRepository,
                recommendationClaimRepository, patchVerifier, clock);
    }

    @Bean
    public RecommendationRunner recommendationRunner(
            RecordingRecommendationManager recordingRecommendationManager,
            @Value(MAX_CONCURRENT_RUNS) int maxConcurrentRuns) {
        return new RecommendationRunner(recordingRecommendationManager, maxConcurrentRuns);
    }

    /**
     * Splits a configured command line on whitespace. Blank configuration yields an empty list, which is
     * what marks the corresponding verification level as not configured.
     */
    private static List<String> splitCommand(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return Arrays.stream(command.trim().split("\\s+")).toList();
    }
}
