/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core;

import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubRuntimeHintsTest {

    private RuntimeHints hints;

    @BeforeEach
    void registerHints() {
        hints = new RuntimeHints();
        new HubRuntimeHints().registerHints(hints, getClass().getClassLoader());
    }

    @Nested
    class Resources {

        @ParameterizedTest
        @ValueSource(strings = {"jeffrey-tag.txt", "scheduler-defaults.properties"})
        void resourceReadByNameIsInTheImage(String resource) {
            assertTrue(RuntimeHintsPredicates.resource().forResource(resource).test(hints));
        }
    }

    @Nested
    class Lz4Codecs {

        /** Each factory's two Java flavours: four LZ4 codecs and four xxHash ones apiece. */
        private static final int REGISTERED_CODECS = 16;

        @Test
        void everyJavaCodecIsRegistered() {
            assertEquals(REGISTERED_CODECS, registeredTypes().size());
        }

        /**
         * The factories find a codec by a class name built from strings, so a codec renamed in a
         * new lz4-java would leave a hint pointing at nothing and the image on a slower codec, or
         * on none. Every registered name has to be a class the jar still has.
         */
        @Test
        void everyRegisteredCodecExistsInLz4Java() {
            ClassLoader classLoader = getClass().getClassLoader();
            registeredTypes().forEach(type -> assertDoesNotThrow(
                    () -> Class.forName(type.getName(), false, classLoader), type.getName()));
        }

        @Test
        void theFactoriesInstantiateEveryRegisteredFlavour() {
            assertDoesNotThrow(LZ4Factory::safeInstance);
            assertDoesNotThrow(LZ4Factory::unsafeInstance);
            assertDoesNotThrow(XXHashFactory::safeInstance);
            assertDoesNotThrow(XXHashFactory::unsafeInstance);
        }

        private List<TypeReference> registeredTypes() {
            return hints.reflection().typeHints()
                    .map(TypeHint::getType)
                    .toList();
        }
    }
}
