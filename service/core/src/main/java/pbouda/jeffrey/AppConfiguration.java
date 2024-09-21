/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pbouda.jeffrey.common.GraphType;
import pbouda.jeffrey.generator.flamegraph.GraphExporterImpl;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.generator.flamegraph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.subsecond.api.SubSecondGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import pbouda.jeffrey.guardian.Guardian;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.manager.action.ChunkBasedRecordingInitializer;
import pbouda.jeffrey.manager.action.ProfilePostCreateActionImpl;
import pbouda.jeffrey.manager.action.ProfileRecordingInitializer;
import pbouda.jeffrey.manager.action.SingleFileRecordingInitializer;
import pbouda.jeffrey.repository.*;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;
import pbouda.jeffrey.viewer.TreeTableEventViewerGenerator;

import java.nio.file.Path;

@Configuration
public class AppConfiguration {

    @Bean
    public JdbcTemplateFactory jdbcTemplateFactory(WorkingDirs workingDirs) {
        return new JdbcTemplateFactory(workingDirs);
    }

    @Bean
    public RecordingManager recordingRepository(WorkingDirs workingDirs) {
        return new FileBasedRecordingManager(workingDirs, new RecordingRepository(workingDirs));
    }

    @Bean
    public SubSecondManager.Factory subSecondFactory(WorkingDirs workingDirs, JdbcTemplateFactory jdbcTemplateFactory) {
        return profileInfo -> new DbBasedSubSecondManager(
                profileInfo, workingDirs, new SubSecondRepository(jdbcTemplateFactory.create(profileInfo)), new SubSecondGeneratorImpl());
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(WorkingDirs workingDirs) {
        return profileInfo -> new AdhocTimeseriesManager(
                profileInfo, workingDirs, new TimeseriesGeneratorImpl());
    }

    @Bean
    public EventViewerManager.Factory eventViewerManager(WorkingDirs workingDirs, JdbcTemplateFactory jdbcTemplateFactory) {
        return profileInfo -> new DbBasedViewerManager(
                workingDirs.profileRecordings(profileInfo),
                new CacheRepository(jdbcTemplateFactory.create(profileInfo)),
                new TreeTableEventViewerGenerator());
    }

    @Bean
    public GraphManager.FlamegraphFactory flamegraphFactory(WorkingDirs workingDirs, JdbcTemplateFactory jdbcTemplateFactory) {
        return profileInfo -> new DbBasedFlamegraphManager(
                profileInfo,
                workingDirs,
                new GraphRepository(jdbcTemplateFactory.create(profileInfo), GraphType.PRIMARY),
                new FlamegraphGeneratorImpl(),
                new GraphExporterImpl(),
                new TimeseriesGeneratorImpl()
        );
    }

    @Bean
    public GraphManager.DiffgraphFactory diffgraphFactory(WorkingDirs workingDirs, JdbcTemplateFactory jdbcTemplateFactory) {
        return (primary, secondary) -> new DbBasedDiffgraphManager(
                primary,
                secondary,
                workingDirs,
                new GraphRepository(jdbcTemplateFactory.create(primary), GraphType.DIFFERENTIAL),
                new DiffgraphGeneratorImpl(),
                new GraphExporterImpl(),
                new TimeseriesGeneratorImpl()
        );
    }

    @Bean
    public GuardianManager.Factory guardianFactory(
            WorkingDirs workingDirs,
            JdbcTemplateFactory jdbcTemplateFactory) {
        return (primary) -> new DbBasedGuardianManager(
                primary,
                workingDirs,
                new Guardian(),
                new CacheRepository(jdbcTemplateFactory.create(primary)),
                new FlamegraphGeneratorImpl(),
                new TimeseriesGeneratorImpl());
    }

    @Bean
    public ProfileManager.Factory profileManager(
            WorkingDirs workingDirs,
            JdbcTemplateFactory jdbcTemplateFactory,
            GraphManager.FlamegraphFactory flamegraphFactory,
            GraphManager.DiffgraphFactory diffgraphFactory,
            SubSecondManager.Factory subSecondFactory,
            TimeseriesManager.Factory timeseriesFactory,
            EventViewerManager.Factory eventViewerManagerFactory,
            GuardianManager.Factory guardianFactory) {

        return profileInfo -> {
            CacheRepository cacheRepository = new CacheRepository(jdbcTemplateFactory.create(profileInfo));

            return new DbBasedProfileManager(
                    profileInfo,
                    workingDirs,
                    flamegraphFactory,
                    diffgraphFactory,
                    subSecondFactory,
                    timeseriesFactory,
                    eventViewerManagerFactory,
                    guardianFactory,
                    new DbBasedProfileInfoManager(profileInfo, workingDirs, cacheRepository),
                    new PersistedProfileAutoAnalysisManager(workingDirs.profileRecordings(profileInfo), cacheRepository));
        };
    }

    @Bean
    public WorkingDirs jeffreyDir(
            @Value("${jeffrey.dir.home}") String homeDir,
            @Value("${jeffrey.dir.recordings}") String recordingsDir,
            @Value("${jeffrey.dir.workspace}") String workspaceDir) {

        return new WorkingDirs(Path.of(homeDir), Path.of(recordingsDir), Path.of(workspaceDir));
    }

    @Bean
    public ProfileRecordingInitializer profileRecordingInitializer(
            @Value("${jeffrey.tools.external.jfr.enabled:true}") boolean jfrToolEnabled,
            @Value("${jeffrey.tools.external.jfr.path:}") Path jfrPath,
            WorkingDirs workingDirs) {

        JdkJfrTool jfrTool = new JdkJfrTool(jfrToolEnabled, jfrPath);
        jfrTool.initialize();

        ProfileRecordingInitializer singleFileRecordingInitializer = new SingleFileRecordingInitializer(workingDirs);
        if (jfrTool.enabled()) {
            return new ChunkBasedRecordingInitializer(workingDirs, jfrTool, singleFileRecordingInitializer);
        } else {
            return singleFileRecordingInitializer;
        }
    }

    @Bean
    public ProfilesManager profilesManager(
            ProfileManager.Factory profileFactory,
            WorkingDirs workingDirs,
            ProfileRecordingInitializer profileRecordingInitializer) {

        return new DbBasedProfilesManager(
                profileFactory, workingDirs, new ProfilePostCreateActionImpl(), profileRecordingInitializer);
    }
}
