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

    private static final String NATIVE_ARTIFACT_ID = "jeffrey-jib-payload-native";
    private static final String JAR_ARTIFACT_ID = "jeffrey-jib-payload-jar";
    private static final String PROVISIONER_BASE_NAME = "provisioner";
    private static final String NO_EXTENSION = "";
    private static final String JAR_EXTENSION = ".jar";

    private static final PayloadSpec NATIVE_SPEC = new PayloadSpec.ArchScoped(
            NATIVE_ARTIFACT_ID, PROVISIONER_BASE_NAME, NO_EXTENSION, PayloadPermissions.EXECUTABLE);

    private static final PayloadSpec JAR_SPEC = new PayloadSpec.Neutral(
            JAR_ARTIFACT_ID, PROVISIONER_BASE_NAME, JAR_EXTENSION, PayloadPermissions.READABLE);

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
     * Parses the {@code provisionerSource} build configuration. An unset value means the default;
     * an unrecognised one is rejected rather than defaulted, because quietly building a {@code
     * native} image for someone who asked for {@code jar} would surface only as a container that
     * fails to profile.
     */
    public static ProvisionerSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NATIVE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(source -> source.kind().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown provisionerSource '" + raw + "'; expected one of " + validValues()));
    }

    private static String validValues() {
        return Arrays.stream(values())
                .map(ProvisionerSource::kind)
                .collect(Collectors.joining(", "));
    }
}
