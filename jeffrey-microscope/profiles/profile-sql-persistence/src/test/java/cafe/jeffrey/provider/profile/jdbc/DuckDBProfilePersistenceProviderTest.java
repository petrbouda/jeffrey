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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.test.DuckDBTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DuckDBTest
class DuckDBProfilePersistenceProviderTest {

    private static final Instant PROFILING_STARTED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TempDir
    Path profilesDir;

    @Test
    void providerConstructsWithArrowOnlyIngestion() {
        DuckDBProfilePersistenceProvider provider = new DuckDBProfilePersistenceProvider(
                profilesDir, FrameResolutionMode.CACHE);

        assertNotNull(provider.databaseManager());
        assertNotNull(provider.eventWriterFactory());
        assertNotNull(provider.repositories());
    }

    @Test
    void eventWritersUseArrowEventWriter(DataSource dataSource) {
        try (DuckDBEventWriters writers =
                     new DuckDBEventWriters(DIRECT_EXECUTOR, dataSource, 10, 100, PROFILING_STARTED_AT)) {
            assertInstanceOf(DuckDBArrowEventWriter.class, writers.events());
        }
    }
}
