package pbouda.jeffrey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.flamegraph.FlamegraphGeneratorImpl;
import pbouda.jeffrey.generator.heatmap.api.D3HeatmapGenerator;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGenerator;
import pbouda.jeffrey.manager.DbBasedProfilesManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.JfrRepository;

import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
public class AppConfiguration {

    private static final Path HOME_DIR = Path.of(System.getProperty("user.home"));
    private static final Path JEFFREY_DIR = HOME_DIR.resolve(".jeffrey");

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
    public ProfilesManager profilesManager(DataSource dataSource, WorkingDirs workingDirs) {
        return new DbBasedProfilesManager(new JdbcTemplate(dataSource), new JfrRepository(workingDirs));
    }

    @Bean
    public DataSource dataSource(WorkingDirs workingDirs) {
        Path profilesFile = workingDirs.jeffreyDir()
                .resolve("profiles.data");

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + profilesFile);
        return dataSource;
    }

    @Bean
    public FlamegraphGenerator flamegraphGenerator() {
        return new FlamegraphGeneratorImpl();
    }

    @Bean
    public HeatmapGenerator heatmapGenerator() {
        return new D3HeatmapGenerator();
    }
}
