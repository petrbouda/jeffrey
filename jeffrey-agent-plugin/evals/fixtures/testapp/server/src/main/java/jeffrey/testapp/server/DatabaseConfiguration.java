package jeffrey.testapp.server;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DatabaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConfiguration.class);

    private static final String POOL_NAME = "testapp-sqlite";

    private static final SQLiteConfig.JournalMode JOURNAL_MODE = SQLiteConfig.JournalMode.WAL;
    private static final SQLiteConfig.SynchronousMode SYNCHRONOUS = SQLiteConfig.SynchronousMode.NORMAL;
    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final SQLiteConfig.TempStore TEMP_STORE = SQLiteConfig.TempStore.MEMORY;
    private static final boolean FOREIGN_KEYS = true;

    /**
     * The pool is created with the no-arg constructor and configured through setters, so it does
     * not start until the first connection is borrowed. That leaves it un-started when
     * jeffrey-tracing-spring-boot-starter's bean post-processor sees it, which is when it attaches
     * its JFR metrics tracker: connection created/acquired/borrowed/timeout events plus the periodic
     * {@code jeffrey.JdbcPoolStatistics} event, all of which feed Jeffrey's connection-pool dashboard.
     */
    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(
            @Value("${database.file}") String file,
            @Value("${database.cleanup-on-shutdown:true}") boolean cleanupOnShutdown,
            @Value("${database.pool.max-size:10}") int maxPoolSize,
            @Value("${database.pool.min-idle:1}") int minIdle) {

        Path dbPath = Path.of(file);
        ensureParentDirectory(dbPath);

        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(JOURNAL_MODE);
        sqliteConfig.setSynchronous(SYNCHRONOUS);
        sqliteConfig.setTempStore(TEMP_STORE);
        sqliteConfig.enforceForeignKeys(FOREIGN_KEYS);
        sqliteConfig.setBusyTimeout(BUSY_TIMEOUT_MS);

        SQLiteDataSource sqliteDataSource = new SQLiteDataSource(sqliteConfig);
        sqliteDataSource.setUrl("jdbc:sqlite:" + dbPath);

        CleanupAwareDataSource dataSource = new CleanupAwareDataSource(dbPath, cleanupOnShutdown);
        dataSource.setPoolName(POOL_NAME);
        dataSource.setDataSource(sqliteDataSource);
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setMinimumIdle(minIdle);
        return dataSource;
    }

    private static void ensureParentDirectory(Path dbPath) {
        Path parent = dbPath.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create parent directory for database file: " + parent, ex);
        }
    }

    static final class CleanupAwareDataSource extends HikariDataSource {

        private static final String[] SQLITE_FILE_SUFFIXES = {"", "-wal", "-shm"};

        private final Path file;
        private final boolean cleanupOnShutdown;

        CleanupAwareDataSource(Path file, boolean cleanupOnShutdown) {
            this.file = file;
            this.cleanupOnShutdown = cleanupOnShutdown;
        }

        @Override
        public void close() {
            super.close();
            if (cleanupOnShutdown) {
                deleteSqliteFiles();
            }
        }

        private void deleteSqliteFiles() {
            String base = file.toString();
            for (String suffix : SQLITE_FILE_SUFFIXES) {
                Path path = Path.of(base + suffix);
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    LOG.warn("Failed to delete SQLite file: path={} error={}", path, ex.toString());
                }
            }
        }
    }
}
