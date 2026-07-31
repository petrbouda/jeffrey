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

package cafe.jeffrey.profile.advisor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.advisor.fleet.FleetPatternsService;
import cafe.jeffrey.profile.advisor.mcp.SourceToolsRegistry;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManager;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManagerFactory;
import cafe.jeffrey.profile.advisor.regression.RegressionService;
import cafe.jeffrey.profile.advisor.run.AdvisorRunner;
import cafe.jeffrey.profile.advisor.run.AdvisorService;
import cafe.jeffrey.profile.advisor.run.AdvisorServiceFactory;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.profile.advisor.source.RecordingCommitResolver;
import cafe.jeffrey.profile.advisor.source.SourceTreeResolver;
import cafe.jeffrey.profile.advisor.verify.CommandRunner;
import cafe.jeffrey.profile.advisor.verify.PatchVerificationSettings;
import cafe.jeffrey.profile.advisor.verify.PatchVerifier;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Wiring for the profile Advisor.
 *
 * <p>The per-profile collaborators are built inside factory lambdas rather than captured around them,
 * for the same reason the flamegraph factory does it: a factory runs once per request, so a settings
 * edit reaches the very next run instead of the next restart. That matters more here than elsewhere —
 * the source folder is the setting without which the feature does nothing at all.</p>
 */
public class AdvisorConfiguration {

    private static final int DEFAULT_MAX_CONCURRENT_RUNS = 2;

    private final ProfileRepositories profileRepositories;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final MicroscopeCoreRepositories coreRepositories;

    public AdvisorConfiguration(
            ProfilePersistenceProvider persistenceProvider,
            DatabaseManagerResolver databaseManagerResolver,
            MicroscopeCorePersistenceProvider corePersistenceProvider) {

        this.profileRepositories = persistenceProvider.repositories();
        this.databaseManagerResolver = databaseManagerResolver;
        this.coreRepositories = corePersistenceProvider.localCoreRepositories();
    }

    @Bean
    public AdvisorSettingsResolver advisorSettingsResolver(Clock clock) {
        return new AdvisorSettingsResolver(coreRepositories.advisorSettingsRepository(), clock);
    }

    @Bean
    public SourceToolsRegistry advisorSourceToolsRegistry() {
        return new SourceToolsRegistry();
    }

    @Bean
    public SourceTreeResolver advisorSourceTreeResolver() {
        return new SourceTreeResolver(
                new RecordingCommitResolver(coreRepositories.recordingTagsRepository()));
    }

    @Bean
    public AdvisorPromptManagerFactory advisorPromptManagerFactory(Clock clock) {
        return profile -> newPromptManager(profile, clock);
    }

    @Bean
    public FleetPatternsService fleetPatternsService() {
        return new FleetPatternsService(coreRepositories.advisorClaimIndexRepository());
    }

    @Bean
    public RegressionService regressionService(
            AdvisorPromptManagerFactory promptManagerFactory, AdvisorSettingsResolver settingsResolver) {

        return new RegressionService(promptManagerFactory, settingsResolver);
    }

    @Bean
    public AdvisorServiceFactory advisorServiceFactory(
            AdvisorSettingsResolver settingsResolver,
            SourceTreeResolver sourceTreeResolver,
            AiChatBackend aiChatBackend,
            SourceToolsRegistry sourceToolsRegistry,
            McpToolsetFactory mcpToolsetFactory,
            Clock clock,
            @Value("${jeffrey.microscope.advisor.verification.git-path:git}")
            String gitPath,
            @Value("${jeffrey.microscope.advisor.verification.timeout-seconds:600}")
            long verificationTimeoutSeconds) {

        return profile -> {
            AdvisorSettings settings = settingsResolver.resolve(profile);
            DataSource profileDb = databaseManagerResolver.open(profile);

            PatchVerifier patchVerifier = new PatchVerifier(
                    new CommandRunner(),
                    new PatchVerificationSettings(
                            gitPath,
                            splitCommand(settings.compileCommand()),
                            splitCommand(settings.testCommand()),
                            Duration.ofSeconds(verificationTimeoutSeconds)));

            return new AdvisorService(
                    newPromptManager(profile, clock),
                    settings,
                    sourceTreeResolver,
                    aiChatBackend,
                    sourceToolsRegistry,
                    mcpToolsetFactory,
                    profileRepositories.newAdvisorRepository(profileDb),
                    coreRepositories.advisorClaimIndexRepository(),
                    patchVerifier,
                    profile.recordingId(),
                    clock);
        };
    }

    @Bean
    public AdvisorRunner advisorRunner(
            AdvisorServiceFactory advisorServiceFactory,
            Clock clock,
            @Value("${jeffrey.microscope.advisor.max-concurrent-runs:" + DEFAULT_MAX_CONCURRENT_RUNS + "}")
            int maxConcurrentRuns) {

        return new AdvisorRunner(advisorServiceFactory, maxConcurrentRuns, clock);
    }

    private AdvisorPromptManager newPromptManager(ProfileInfo profile, Clock clock) {
        DataSource profileDb = databaseManagerResolver.open(profile);
        return new AdvisorPromptManager(
                profileRepositories.newEventTypeRepository(profileDb),
                profileRepositories.newEventStreamRepository(profileDb),
                profileRepositories.newAdvisorRepository(profileDb),
                new ProfilingStartEnd(profile.profilingStartedAt(), profile.profilingFinishedAt()),
                clock);
    }

    /**
     * Splits an operator-configured build command into its argv. Whitespace splitting is deliberately
     * naive: a command that needs quoting or shell operators belongs in a script the operator owns, not
     * in a settings field a profiler runs on their behalf.
     */
    private static List<String> splitCommand(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return Arrays.stream(command.strip().split("\\s+")).toList();
    }
}
