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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Which build of the provisioner an image carries.
 *
 * <p>{@link #NATIVE} is the default: a GraalVM binary the entrypoint runs directly, which assumes
 * nothing about the application's JVM and starts in milliseconds. It costs around 44 MB per
 * architecture, and because Jib layers are not per-platform a multi-platform image pays that twice.
 *
 * <p>{@link #JAR} bakes the provisioner jar instead and runs it with the application's own
 * {@code java}. That is guaranteed to exist — the extension refuses to wrap a build plan whose
 * entrypoint is not a java command — so the jar needs no JVM discovery, is one arch-neutral file of
 * around 4 MB, and removes the architecture dimension from the provisioner entirely. In exchange it
 * boots a second JVM before the application, and the application's JVM must be new enough to read
 * the jar's class files.
 */
public enum ProvisionerSource {

    NATIVE {
        @Override
        public PayloadSpec spec() {
            return NATIVE_SPEC;
        }
    },
    JAR {
        @Override
        public PayloadSpec spec() {
            return JAR_SPEC;
        }
    };

    private static final String PROVISIONER_BASE_NAME = "provisioner";
    private static final String NO_EXTENSION = "";
    private static final String JAR_EXTENSION = ".jar";

    private static final PayloadSpec NATIVE_SPEC = new PayloadSpec.ArchScoped(
            PROVISIONER_BASE_NAME, NO_EXTENSION, PayloadPermissions.EXECUTABLE);

    private static final PayloadSpec JAR_SPEC = new PayloadSpec.Neutral(
            PROVISIONER_BASE_NAME, JAR_EXTENSION, PayloadPermissions.READABLE);

    /** How this source names and installs its payload. */
    public abstract PayloadSpec spec();

    /**
     * The value baked as {@code JEFFREY_PROVISIONER_KIND}. The entrypoint reads it to decide
     * whether to exec the provisioner or hand it to the application's JVM; the path itself always
     * arrives in {@code JEFFREY_PROVISIONER_PATH}, whichever build it points at.
     */
    public String kind() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses the {@code kind} entry of a payload descriptor. The value is stated by the payload jar
     * that was published, so there is nothing to default: a missing or unrecognised one means a
     * broken payload, and quietly treating it as {@code native} would surface only as a container
     * that fails to profile.
     */
    public static ProvisionerSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing provisioner kind; expected one of " + validValues());
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(source -> source.kind().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown provisioner kind '" + raw + "'; expected one of " + validValues()));
    }

    private static String validValues() {
        return Arrays.stream(values())
                .map(ProvisionerSource::kind)
                .collect(Collectors.joining(", "));
    }
}
