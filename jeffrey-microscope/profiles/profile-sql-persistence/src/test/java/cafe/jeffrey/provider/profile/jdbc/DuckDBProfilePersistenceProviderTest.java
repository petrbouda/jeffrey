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

import cafe.jeffrey.shared.common.EventWriterMode;
import cafe.jeffrey.shared.common.FrameResolutionMode;
import cafe.jeffrey.test.DuckDBTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DuckDBTest
class DuckDBProfilePersistenceProviderTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TempDir
    Path profilesDir;

    @Nested
    class EventWriterModeSelection {

        @Test
        void appenderConfigSelectsAppenderWriter() {
            DuckDBProfilePersistenceProvider provider = new DuckDBProfilePersistenceProvider(
                    FIXED_CLOCK, profilesDir, FrameResolutionMode.CACHE, EventWriterMode.APPENDER);

            assertEquals(EventWriterMode.APPENDER, provider.eventWriterMode());
        }

        @Test
        void arrowConfigSelectsArrowWriterWhenRuntimeAvailable() {
            DuckDBProfilePersistenceProvider provider = new DuckDBProfilePersistenceProvider(
                    FIXED_CLOCK, profilesDir, FrameResolutionMode.CACHE, EventWriterMode.ARROW);

            EventWriterMode expected = ArrowRuntimeSupport.isAvailable()
                    ? EventWriterMode.ARROW
                    : EventWriterMode.APPENDER;
            assertEquals(expected, provider.eventWriterMode());
        }

        @Test
        void defaultModeIsArrow() {
            DuckDBProfilePersistenceProvider provider = new DuckDBProfilePersistenceProvider(
                    FIXED_CLOCK, profilesDir, FrameResolutionMode.CACHE);

            EventWriterMode expected = ArrowRuntimeSupport.isAvailable()
                    ? EventWriterMode.ARROW
                    : EventWriterMode.APPENDER;
            assertEquals(expected, provider.eventWriterMode());
        }
    }

    @Nested
    class EventWritersFactories {

        @Test
        void appenderBasedWritersUseAppenderEventWriter(DataSource dataSource) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (DuckDBEventWriters writers = DuckDBEventWriters.appenderBased(executor, dataSource, 10)) {
                assertInstanceOf(DuckDBEventWriter.class, writers.events());
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        void arrowBasedWritersUseArrowEventWriter(DataSource dataSource) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (DuckDBEventWriters writers =
                         DuckDBEventWriters.arrowBased(executor, DIRECT_EXECUTOR, dataSource, 10, 100)) {
                assertInstanceOf(DuckDBArrowEventWriter.class, writers.events());
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
