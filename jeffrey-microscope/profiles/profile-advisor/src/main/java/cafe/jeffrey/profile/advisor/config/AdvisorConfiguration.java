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
import cafe.jeffrey.profile.advisor.mcp.SourceToolsRegistry;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManager;
import cafe.jeffrey.profile.advisor.prompt.AdvisorPromptManagerFactory;
import cafe.jeffrey.profile.advisor.run.AdvisorRunResultWriter;
import cafe.jeffrey.profile.advisor.run.AdvisorRunner;
import cafe.jeffrey.profile.advisor.run.AdvisorService;
import cafe.jeffrey.profile.advisor.run.AdvisorServiceFactory;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettingsResolver;
import cafe.jeffrey.profile.advisor.source.RecordingCommitResolver;
import cafe.jeffrey.profile.advisor.source.SourceTreeResolver;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.PipelineRunRepository;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProfilingStartEnd;

import javax.sql.DataSource;
import java.time.Clock;

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
    public AdvisorSettingsResolver advisorSettingsResolver(SettingsStore settingsStore, Clock clock) {
        return new AdvisorSettingsResolver(coreRepositories.advisorSettingsRepository(), settingsStore, clock);
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
    public AdvisorServiceFactory advisorServiceFactory(
            AdvisorSettingsResolver settingsResolver,
            SourceTreeResolver sourceTreeResolver,
            AiChatBackend aiChatBackend,
            SourceToolsRegistry sourceToolsRegistry,
            McpToolsetFactory mcpToolsetFactory,
            Clock clock) {

        return profile -> {
            AdvisorSettings settings = settingsResolver.resolve(profile);
            DataSource profileDb = databaseManagerResolver.open(profile);

            return new AdvisorService(
                    newPromptManager(profile, clock),
                    settings,
                    sourceTreeResolver,
                    aiChatBackend,
                    sourceToolsRegistry,
                    mcpToolsetFactory,
                    profileRepositories.newAdvisorRepository(profileDb),
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

        return new AdvisorRunner(advisorServiceFactory, runResultWriter(), maxConcurrentRuns, clock);
    }

    /**
     * Stores a finished batch's timeline in the profile database, one {@code pipeline_runs} row per event
     * type — the same table heap-dump initialization stores its run in. Built here rather than injected so
     * the runner stays free of persistence types; opens the profile DB per call, like the other factories.
     */
    private AdvisorRunResultWriter runResultWriter() {
        return (profile, runs) -> {
            DataSource profileDb = databaseManagerResolver.open(profile);
            PipelineRunRepository repository = profileRepositories.newPipelineRunRepository(profileDb);
            runs.forEach(repository::upsert);
        };
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
}
