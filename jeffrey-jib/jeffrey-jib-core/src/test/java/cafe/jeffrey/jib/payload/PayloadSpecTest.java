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

package cafe.jeffrey.jib.payload;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The naming rule in isolation. It is pinned here as well as end-to-end because the entrypoint
 * script's {@code {arch}} expansion is written against it: if these two drift, a multi-platform
 * image looks for a file that is not there and silently starts unprofiled.
 */
class PayloadSpecTest {

    private static final PayloadSpec ARCH_SCOPED = new PayloadSpec.ArchScoped(
            "provisioner", "", PayloadPermissions.EXECUTABLE);

    private static final PayloadSpec NEUTRAL = new PayloadSpec.Neutral(
            "provisioner", ".jar", PayloadPermissions.READABLE);

    private static Set<String> architectures(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Nested
    class SinglePlatform {

        @Test
        void archScopedUsesUnsuffixedNames() {
            List<PayloadRequest> requests = ARCH_SCOPED.requestsFor(architectures("amd64"));

            assertEquals(1, requests.size());
            assertEquals("/opt/jeffrey/provisioner", requests.get(0).installPath().toString());
            assertEquals("jeffrey-payload/provisioner-linux-amd64", requests.get(0).resource());
            assertEquals("/opt/jeffrey/provisioner", ARCH_SCOPED.envPath(architectures("amd64")));
        }

        @Test
        void neutralHasOneArchIndependentResource() {
            List<PayloadRequest> requests = NEUTRAL.requestsFor(architectures("amd64"));

            assertEquals(1, requests.size());
            assertEquals("jeffrey-payload/provisioner.jar", requests.get(0).resource());
            assertEquals("/opt/jeffrey/provisioner.jar", NEUTRAL.envPath(architectures("amd64")));
        }
    }

    @Nested
    class MultiPlatform {

        @Test
        void archScopedSuffixesEveryFileAndBakesThePlaceholder() {
            Set<String> both = architectures("amd64", "arm64");

            List<PayloadRequest> requests = ARCH_SCOPED.requestsFor(both);

            assertEquals(
                    List.of("/opt/jeffrey/provisioner-amd64", "/opt/jeffrey/provisioner-arm64"),
                    requests.stream().map(r -> r.installPath().toString()).toList());
            assertEquals("/opt/jeffrey/provisioner-{arch}", ARCH_SCOPED.envPath(both));
        }

        @Test
        void archScopedKeepsTheExtensionAfterTheSuffix() {
            PayloadSpec profiler = new PayloadSpec.ArchScoped(
                    "libasyncProfiler", ".so", PayloadPermissions.READABLE);

            assertEquals(
                    "/opt/jeffrey/libasyncProfiler-{arch}.so",
                    profiler.envPath(architectures("amd64", "arm64")));
        }

        @Test
        void neutralIsUnaffectedByTheArchitectureCount() {
            Set<String> both = architectures("amd64", "arm64");

            assertEquals(1, NEUTRAL.requestsFor(both).size());
            assertEquals("/opt/jeffrey/provisioner.jar", NEUTRAL.envPath(both));
        }
    }
}
