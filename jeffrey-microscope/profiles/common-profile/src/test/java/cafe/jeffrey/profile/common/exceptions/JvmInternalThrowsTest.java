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

package cafe.jeffrey.profile.common.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JvmInternalThrowsTest {

    private static final ThrowFrame APPLICATION_FRAME =
            new ThrowFrame("com.acme.orders.OrderService", "place", 42);

    @Nested
    @DisplayName("class rule")
    class ClassRule {

        @Test
        void resolutionFailureIsJvmInternalWhereverItWasThrown() {
            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.lang.ClassNotFoundException", APPLICATION_FRAME));
            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.lang.NoSuchMethodError", APPLICATION_FRAME));
            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.lang.invoke.LambdaConversionException", APPLICATION_FRAME));
        }

        @Test
        void withoutAStackOnlyTheClassDecides() {
            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.lang.NoSuchFieldException", null));
            assertEquals(ThrowOrigin.APPLICATION,
                    JvmInternalThrows.classify("java.io.IOException", null));
        }

        @Test
        void unknownClassIsApplication() {
            assertEquals(ThrowOrigin.APPLICATION, JvmInternalThrows.classify(null, null));
        }
    }

    @Nested
    @DisplayName("thrower rule")
    class ThrowerRule {

        @Test
        void throwFromAClassLoaderIsJvmInternal() {
            ThrowFrame loader = new ThrowFrame("jdk.internal.loader.URLClassPath$JarLoader", "getResource", 1053);

            assertEquals(ThrowOrigin.JVM_INTERNAL, JvmInternalThrows.classify("java.io.IOException", loader));
        }

        @Test
        void throwFromTheInvokeLayerIsJvmInternal() {
            ThrowFrame linker = new ThrowFrame("java.lang.invoke.MethodHandleNatives", "linkCallSite", 274);

            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.lang.IllegalArgumentException", linker));
        }

        @Test
        void springBootLoaderIsAClassLoader() {
            ThrowFrame bootLoader = new ThrowFrame(
                    "org.springframework.boot.loader.net.protocol.jar.JarUrlConnection", "connect", 270);

            assertEquals(ThrowOrigin.JVM_INTERNAL,
                    JvmInternalThrows.classify("java.io.FileNotFoundException", bootLoader));
        }
    }

    @Nested
    @DisplayName("libraries stay application")
    class Libraries {

        @Test
        void socketTimeoutInsideNettyIsApplication() {
            ThrowFrame netty = new ThrowFrame("io.netty.handler.timeout.ReadTimeoutHandler", "readTimedOut", 98);

            assertEquals(ThrowOrigin.APPLICATION,
                    JvmInternalThrows.classify("java.net.SocketTimeoutException", netty));
        }

        @Test
        void logbackFailureIsApplication() {
            ThrowFrame logback = new ThrowFrame("ch.qos.logback.core.OutputStreamAppender", "writeBytes", 199);

            assertEquals(ThrowOrigin.APPLICATION,
                    JvmInternalThrows.classify("java.io.IOException", logback));
        }

        @Test
        void jdkCodeOutsideTheMachineryIsApplication() {
            ThrowFrame parser = new ThrowFrame("java.lang.Integer", "parseInt", 661);

            assertEquals(ThrowOrigin.APPLICATION,
                    JvmInternalThrows.classify("java.lang.NumberFormatException", parser));
        }
    }
}
