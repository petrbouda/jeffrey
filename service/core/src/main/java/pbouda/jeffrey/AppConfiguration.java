package pbouda.jeffrey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGeneratorImpl;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGeneratorImpl;
import pbouda.jeffrey.graph.GraphExporterImpl;
import pbouda.jeffrey.graph.diff.DiffgraphGeneratorImpl;
import pbouda.jeffrey.graph.flame.FlamegraphGeneratorImpl;
import pbouda.jeffrey.manager.*;
import pbouda.jeffrey.repository.*;

import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
public class AppConfiguration {

    private static final Path HOME_DIR = Path.of(System.getProperty("user.home"));
    private static final Path JEFFREY_DIR = HOME_DIR.resolve(".jeffrey");

    @Bean
    public DataSource dataSource(WorkingDirs workingDirs) {
        Path profilesFile = workingDirs.jeffreyDir()
                .resolve("profiles.data");

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + profilesFile);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public RecordingManager recordingRepository(JdbcTemplate jdbcTemplate, WorkingDirs workingDirs) {
        return new FileBasedRecordingManager(
                workingDirs, new ProfileRepository(jdbcTemplate), new RecordingRepository(workingDirs));
    }

    @Bean
    public HeatmapManager.Factory heatmapFactory(JdbcTemplate jdbcTemplate) {
        return profileInfo -> new DbBasedHeatmapManager(
                profileInfo, new HeatmapRepository(jdbcTemplate), new HeatmapGeneratorImpl());
    }

    @Bean
    public TimeseriesManager.Factory timeseriesFactory(JdbcTemplate jdbcTemplate) {
        return profileInfo -> new DbBasedTimeseriesManager(
                profileInfo, new TimeseriesRepository(jdbcTemplate), new TimeseriesGeneratorImpl());
    }

    @Bean
    public GraphManager.FlamegraphFactory flamegraphFactory(WorkingDirs workingDirs, JdbcTemplate jdbcTemplate) {
        return profileInfo -> new DbBasedFlamegraphManager(
                profileInfo,
                workingDirs,
                new GraphRepository(jdbcTemplate, GraphType.FLAMEGRAPH),
                new FlamegraphGeneratorImpl(),
                new GraphExporterImpl()
        );
    }

    @Bean
    public GraphManager.DiffgraphFactory diffgraphFactory(WorkingDirs workingDirs, JdbcTemplate jdbcTemplate) {
        return (primary, secondary) -> new DbBasedDiffgraphManager(
                primary,
                secondary,
                workingDirs,
                new GraphRepository(jdbcTemplate, GraphType.DIFFGRAPH),
                new DiffgraphGeneratorImpl(),
                new GraphExporterImpl()
        );
    }

    @Bean
    public ProfileManager.Factory profileManager(
            JdbcTemplate jdbcTemplate,
            GraphManager.FlamegraphFactory flamegraphFactory,
            GraphManager.DiffgraphFactory diffgraphFactory,
            HeatmapManager.Factory heatmapFactory,
            TimeseriesManager.Factory timeseriesFactory) {

        return profileInfo -> new DbBasedProfileManager(
                profileInfo,
                new ProfileRepository(jdbcTemplate),
                flamegraphFactory,
                diffgraphFactory,
                heatmapFactory,
                timeseriesFactory,
                new DbBasedProfileInfoManager(profileInfo, new CommonRepository(jdbcTemplate))
        );
    }

    @Bean
    public WorkingDirs jeffreyDir(@Value("${jeffrey.homeDir:}") String jeffreyDir) {
        Path result;
        if (jeffreyDir.isBlank()) {
            result = JEFFREY_DIR;
        } else {
            result = Path.of(jeffreyDir);
        }
        FileUtils.createDirectories(result);
        return new WorkingDirs(result);
    }


    @Bean
    public ProfilesManager profilesManager(
            RecordingManager recordingManager, ProfileManager.Factory profileFactory, JdbcTemplate jdbcTemplate) {

        return new DbBasedProfilesManager(recordingManager, profileFactory, jdbcTemplate);
    }
}
