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

package cafe.jeffrey.microscope.runtime;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.DuckDBMicroscopeCorePersistenceProvider;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.provider.profile.api.ProfilePersistenceProvider;
import cafe.jeffrey.provider.profile.jdbc.DatabaseManagerResolverImpl;
import cafe.jeffrey.provider.profile.jdbc.DuckDBProfilePersistenceProvider;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.shared.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Opens a Jeffrey home directory: the directory layout, the core database and the per-profile
 * database provider. Every Microscope process — the full application as much as the MCP-only one —
 * starts from these beans, so the two always agree on where the data lives and how it is opened.
 */
@Configuration
@Import(JacksonConfiguration.class)
public class MicroscopeRuntimeConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MicroscopeRuntimeConfiguration.class);

    private static final String CORE_DATABASE_FILENAME = "jeffrey-data.db";
    private static final String DUCKDB_JDBC_URL_PREFIX = "jdbc:duckdb:";

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public MicroscopeJeffreyDirs jeffreyDir(
            @Value("${jeffrey.microscope.home.dir:${user.home}/.jeffrey}") String homeDir,
            @Value("${jeffrey.microscope.temp.dir:}") String tempDir) {

        Path homeDirPath = Path.of(homeDir);
        MicroscopeJeffreyDirs jeffreyDirs = StringUtils.isNullOrBlank(tempDir)
                ? new MicroscopeJeffreyDirs(homeDirPath)
                : new MicroscopeJeffreyDirs(homeDirPath, Path.of(tempDir));

        jeffreyDirs.initialize();
        LOG.info("Using Jeffrey directory: HOME={} TEMP={}", jeffreyDirs.homeDir(), jeffreyDirs.temp());
        return jeffreyDirs;
    }

    @Bean
    public MicroscopeCorePersistenceProvider platformPersistenceProvider(
            MicroscopeJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.microscope.persistence.database.url:}") String databaseUrl,
            Clock clock) {

        String resolvedUrl = StringUtils.isNullOrBlank(databaseUrl)
                ? DUCKDB_JDBC_URL_PREFIX + jeffreyDirs.homeDir().resolve(CORE_DATABASE_FILENAME)
                : databaseUrl;

        DuckDBMicroscopeCorePersistenceProvider provider = new DuckDBMicroscopeCorePersistenceProvider();
        provider.initialize(resolvedUrl, clock);
        return provider;
    }

    @Bean
    public ProfilePersistenceProvider profilePersistenceProvider(
            MicroscopeJeffreyDirs jeffreyDirs,
            @Value("${jeffrey.microscope.profile.frame-resolution:CACHE}") FrameResolutionMode frameResolutionMode,
            Clock clock) {

        LOG.info("Using frame resolution mode: mode={}", frameResolutionMode);
        return new DuckDBProfilePersistenceProvider(jeffreyDirs.profiles(), frameResolutionMode, clock);
    }

    @Bean
    public DatabaseManagerResolver databaseManagerResolver(ProfilePersistenceProvider profilePersistenceProvider) {
        return new DatabaseManagerResolverImpl(profilePersistenceProvider.databaseManager());
    }
}
