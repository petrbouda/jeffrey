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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroscopeHomeResolverTest {

    private static final String[] NO_ARGS = new String[0];

    private static Properties properties(String... keyValues) {
        Properties properties = new Properties();
        properties.setProperty("user.home", "/home/tester");
        for (int i = 0; i < keyValues.length; i += 2) {
            properties.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return properties;
    }

    @Nested
    class HomeDirPrecedence {

        @Test
        void defaultsToUserHomeSubdirectory() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(Map.of(), properties());

            ResolvedBootConfig config = resolver.resolve(NO_ARGS);

            assertEquals(Path.of("/home/tester/.jeffrey-microscope"), config.homeDir());
        }

        @Test
        void environmentVariableOverridesDefault() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(
                    Map.of("JEFFREY_MICROSCOPE_HOME_DIR", "/env/home"), properties());

            ResolvedBootConfig config = resolver.resolve(NO_ARGS);

            assertEquals(Path.of("/env/home"), config.homeDir());
        }

        @Test
        void systemPropertyOverridesEnvironmentVariable() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(
                    Map.of("JEFFREY_MICROSCOPE_HOME_DIR", "/env/home"),
                    properties("jeffrey.microscope.home.dir", "/prop/home"));

            ResolvedBootConfig config = resolver.resolve(NO_ARGS);

            assertEquals(Path.of("/prop/home"), config.homeDir());
        }

        @Test
        void commandLineArgumentOverridesEverything() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(
                    Map.of("JEFFREY_MICROSCOPE_HOME_DIR", "/env/home"),
                    properties("jeffrey.microscope.home.dir", "/prop/home"));

            ResolvedBootConfig config = resolver.resolve(
                    new String[]{"--jeffrey.microscope.home.dir=/cli/home"});

            assertEquals(Path.of("/cli/home"), config.homeDir());
        }
    }

    @Nested
    class PortAndDatabaseUrl {

        @Test
        void defaultsToPort8080WithoutExternalDatabase() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(Map.of(), properties());

            ResolvedBootConfig config = resolver.resolve(NO_ARGS);

            assertEquals(8080, config.port());
            assertTrue(config.externalDatabaseUrl().isEmpty());
        }

        @Test
        void resolvesPortAndDatabaseUrlFromArguments() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(Map.of(), properties());

            ResolvedBootConfig config = resolver.resolve(new String[]{
                    "--server.port=9999",
                    "--jeffrey.microscope.persistence.database.url=jdbc:duckdb:/somewhere/db"});

            assertEquals(9999, config.port());
            assertEquals("jdbc:duckdb:/somewhere/db", config.externalDatabaseUrl().orElseThrow());
        }

        @Test
        void unparsablePortFallsBackToDefault() {
            MicroscopeHomeResolver resolver = new MicroscopeHomeResolver(Map.of(), properties());

            ResolvedBootConfig config = resolver.resolve(new String[]{"--server.port=not-a-number"});

            assertEquals(8080, config.port());
        }
    }

    @Nested
    class ArgsRewriting {

        @Test
        void appendsHomeDirArgument() {
            String[] rewritten = MicroscopeHomeResolver.withHomeDir(
                    new String[]{"--server.port=9000"}, Path.of("/resolved/home"));

            assertEquals(
                    List.of("--server.port=9000", "--jeffrey.microscope.home.dir=/resolved/home"),
                    List.of(rewritten));
        }

        @Test
        void replacesExistingHomeDirArgument() {
            String[] rewritten = MicroscopeHomeResolver.withHomeDir(
                    new String[]{"--jeffrey.microscope.home.dir=/old/home", "--server.port=9000"},
                    Path.of("/new/home"));

            assertEquals(
                    List.of("--server.port=9000", "--jeffrey.microscope.home.dir=/new/home"),
                    List.of(rewritten));
        }
    }
}
