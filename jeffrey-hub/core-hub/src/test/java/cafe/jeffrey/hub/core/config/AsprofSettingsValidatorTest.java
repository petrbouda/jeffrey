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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.shared.common.config.ConfigType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsprofSettingsValidatorTest {

    private final AsprofSettingsValidator validator = new AsprofSettingsValidator();

    @Nested
    class Accepts {

        @Test
        void aPlainCommand() {
            assertDoesNotThrow(() -> validator.validate("-agentpath:/opt/lib.so=start,cpu"));
        }

        @Test
        void everyPlaceholderTheProvisionerCanAnswer() {
            assertDoesNotThrow(() -> validator.validate(
                    "-agentpath:<<JEFFREY:PROFILER_PATH>>=start,file=<<JEFFREY:CURRENT_SESSION>>/p-%t.jfr"));
        }

        /** Environment placeholders are the deployment's business, not something the hub can check. */
        @Test
        void anEnvironmentPlaceholder() {
            assertDoesNotThrow(() -> validator.validate("-agentpath:/opt/lib.so=start,tag=<<ENV:CLUSTER>>"));
        }

        @Test
        void aPlaceholderWithADefault() {
            assertDoesNotThrow(() -> validator.validate("<<JEFFREY:PROFILER_PATH:-/opt/lib.so>>"));
        }
    }

    @Nested
    class Rejects {

        @Test
        void anEmptyCommand() {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(""));
            assertThrows(IllegalArgumentException.class, () -> validator.validate("   "));
            assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
        }

        /**
         * An unknown placeholder is not inert: the provisioner substitutes an empty string and logs,
         * so the JVM would start with a command quietly missing a path. Better to fail while the
         * operator is still looking at it.
         */
        @Test
        void aPlaceholderNothingCanResolve() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> validator.validate("-agentpath:<<JEFFREY:NOPE>>=start"));

            assertTrue(thrown.getMessage().contains("NOPE"), "the message must name the bad placeholder");
        }

        /** An argfile is line-based, so a value spanning lines would not survive the round trip. */
        @Test
        void aCommandSpanningLines() {
            assertThrows(IllegalArgumentException.class, () -> validator.validate("start\n-XX:+UseG1GC"));
        }

        @Test
        void aCommandLongerThanTheCap() {
            assertThrows(IllegalArgumentException.class, () -> validator.validate("x".repeat(8193)));
        }
    }

    @Nested
    class Catalogue {

        /**
         * A type stored without a validator would be a value nobody checked, so the lookup refuses
         * to initialise rather than letting one through.
         */
        @Test
        void everyConfigTypeHasAValidator() {
            for (ConfigType type : ConfigType.values()) {
                assertDoesNotThrow(() -> ConfigValueValidators.validate(type, "-agentpath:/opt/lib.so=start"));
            }
        }
    }
}
