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

package cafe.jeffrey.microscope.core.recovery;

import cafe.jeffrey.shared.persistence.schema.SchemaValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Pre-flight guard that runs in {@code main()} BEFORE {@code SpringApplication.run}. It resolves
 * the Jeffrey home directory, validates the existing core database against this build's
 * migrations and — when the database is incompatible — serves the recovery page instead of
 * crashing. After the user picks a recovery action the same JVM continues into the normal
 * Spring Boot startup, so no process restart is needed.
 *
 * <p>Switching to a different home folder applies to the current run only: if the configured
 * home still points at the incompatible folder on the next start, validation fails again and
 * the recovery page is shown again.
 */
public final class MicroscopeStartupGuard {

    private static final Logger LOG = LoggerFactory.getLogger(MicroscopeStartupGuard.class);

    private static final String BANNER = """
            ================================================================================
             Jeffrey cannot start: the data folder was created by an older version
               Home dir : %s

             Jeffrey is running in RECOVERY MODE — open  http://localhost:%d  to fix it:
               1. Start fresh          removes the current data folder and starts again
               2. Use another folder   points Jeffrey at a different home folder
            ================================================================================
            """;

    private static final String EXTERNAL_DATABASE_BANNER = """
            ================================================================================
             Jeffrey cannot start: the configured database was created by an older version
               Database URL : %s

             The database is managed externally, so Jeffrey cannot fix it by itself.
             Recreate the database (or point Jeffrey at a new one) and start again.
            ================================================================================
            """;

    private static final String FALLBACK_PORT_BANNER = """
            ================================================================================
             Note: port %d is already in use, so the recovery page is served on a fallback
             port (see the address above). Jeffrey itself still needs port %d after the
             recovery completes — free it, or restart with --server.port=<port>.
            ================================================================================
            """;

    private static final String SERVER_FAILED_BANNER = """
            ================================================================================
             Jeffrey cannot start: the data folder was created by an older version, and the
             recovery page cannot be served: %s

             Resolve the error and try again, or fix the data folder manually.
            ================================================================================
            """;

    private MicroscopeStartupGuard() {
    }

    /**
     * Returns the args to boot Spring with; the home-dir argument is pinned to the resolved
     * (possibly redirected or recovered) home directory.
     */
    public static String[] guard(String[] args) {
        ResolvedBootConfig config = MicroscopeHomeResolver.fromSystem().resolve(args);

        if (config.externalDatabaseUrl().isPresent()) {
            guardExternalDatabase(config.externalDatabaseUrl().get());
            return args;
        }

        Path homeDir = config.homeDir();
        SchemaValidationResult result = CoreDatabaseCompatibilityCheck.checkHome(homeDir);
        while (CoreDatabaseCompatibilityCheck.requiresRecovery(result)) {
            homeDir = recover(homeDir, config.port(), result);
            result = CoreDatabaseCompatibilityCheck.checkHome(homeDir);
        }

        return MicroscopeHomeResolver.withHomeDir(args, homeDir);
    }

    private static Path recover(Path homeDir, int port, SchemaValidationResult result) {
        LOG.warn("Existing database is incompatible with this Jeffrey version: home_dir={} detail={}",
                homeDir, describe(result));

        Path currentHome = homeDir;
        try (RecoveryWebServer server = new RecoveryWebServer(
                port,
                currentHome,
                () -> RecoveryActions.startFresh(currentHome),
                rawPath -> RecoveryActions.switchHome(currentHome, rawPath))) {

            System.out.printf(BANNER, homeDir, server.port());
            if (server.usesFallbackPort()) {
                System.out.printf(FALLBACK_PORT_BANNER, port, port);
            }

            Path newHome = server.awaitAction();
            LOG.info("Recovery action completed, continuing startup: home_dir={}", newHome);
            return newHome;
        } catch (IOException e) {
            LOG.error("Cannot start the recovery web server: port={} message={}", port, e.getMessage());
            System.out.printf(SERVER_FAILED_BANNER, e.getMessage());
            System.exit(1);
            return homeDir;
        }
    }

    private static void guardExternalDatabase(String databaseUrl) {
        SchemaValidationResult result = CoreDatabaseCompatibilityCheck.checkUrl(databaseUrl);
        if (CoreDatabaseCompatibilityCheck.requiresRecovery(result)) {
            LOG.error("External database is incompatible with this Jeffrey version: database_url={} detail={}",
                    databaseUrl, describe(result));
            System.out.printf(EXTERNAL_DATABASE_BANNER, databaseUrl);
            System.exit(1);
        }
    }

    private static String describe(SchemaValidationResult result) {
        return switch (result) {
            case SchemaValidationResult.Incompatible incompatible -> String.join("; ", incompatible.details());
            case SchemaValidationResult.Unreadable unreadable -> unreadable.message();
            case SchemaValidationResult.Valid ignored -> "valid";
            case SchemaValidationResult.Migratable ignored -> "migratable";
        };
    }
}
