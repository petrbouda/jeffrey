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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * What the payload jar on the extension's classpath carries.
 *
 * <p>Binaries reach the extension as ordinary plugin dependencies: {@code jeffrey-jib-maven-jar}
 * and friends pull in exactly one payload jar, and this descriptor is the file inside it that
 * says which flavour it is and where its binaries came from. Nothing is resolved or downloaded by
 * the extension itself, so which provisioner an image gets is decided by the dependency the build
 * declares and by nothing else.
 *
 * @param kind                  which provisioner build the payload carries
 * @param provisionerProvenance the Jeffrey release the provisioner was taken from, for the build log
 * @param profilerProvenance    the async-profiler release, for the build log
 */
public record PayloadDescriptor(ProvisionerSource kind, String provisionerProvenance, String profilerProvenance) {

    /** Where every payload jar keeps its descriptor; the extension finds the jar by this file. */
    public static final String RESOURCE = "jeffrey-payload/payload.properties";

    static final String KIND_KEY = "kind";
    static final String PROVISIONER_PROVENANCE_KEY = "provisioner.provenance";
    static final String PROFILER_PROVENANCE_KEY = "profiler.provenance";

    public PayloadDescriptor {
        if (kind == null) {
            throw new IllegalArgumentException("Payload kind must not be null");
        }
        provisionerProvenance = requireText(provisionerProvenance, PROVISIONER_PROVENANCE_KEY);
        profilerProvenance = requireText(profilerProvenance, PROFILER_PROVENANCE_KEY);
    }

    private static String requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payload descriptor entry '" + key + "' must not be blank");
        }
        return value.trim();
    }

    /**
     * Finds the one payload jar on the class path.
     *
     * @throws PayloadResolutionException no payload jar is present — the build declared the bare
     *                                    extension instead of a flavour — or more than one is,
     *                                    which would leave the choice to class-path order
     */
    public static PayloadDescriptor discover(ClassLoader classLoader) throws PayloadResolutionException {
        List<URL> descriptors = locate(classLoader);
        if (descriptors.isEmpty()) {
            throw new PayloadResolutionException(RESOURCE,
                    "no payload jar is on the extension's class path. Declare a flavour of the extension "
                            + "as the jib plugin dependency — jeffrey-jib-maven-jar / jeffrey-jib-maven-native "
                            + "(or the jeffrey-jib-gradle-* equivalents) — rather than jeffrey-jib-maven itself");
        }
        if (descriptors.size() > 1) {
            throw new PayloadResolutionException(RESOURCE,
                    "more than one payload jar is on the extension's class path, so the provisioner build "
                            + "would depend on class-path order. Declare exactly one flavour: " + descriptors);
        }
        return load(descriptors.get(0));
    }

    private static List<URL> locate(ClassLoader classLoader) throws PayloadResolutionException {
        try {
            return new ArrayList<>(Collections.list(classLoader.getResources(RESOURCE)));
        } catch (IOException e) {
            throw new PayloadResolutionException(RESOURCE, "the class path could not be scanned", e);
        }
    }

    private static PayloadDescriptor load(URL descriptor) throws PayloadResolutionException {
        Properties properties = new Properties();
        try (InputStream in = descriptor.openStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new PayloadResolutionException(RESOURCE, "the descriptor could not be read from " + descriptor, e);
        }
        try {
            return new PayloadDescriptor(
                    ProvisionerSource.parse(properties.getProperty(KIND_KEY)),
                    properties.getProperty(PROVISIONER_PROVENANCE_KEY),
                    properties.getProperty(PROFILER_PROVENANCE_KEY));
        } catch (IllegalArgumentException e) {
            throw new PayloadResolutionException(RESOURCE, "the descriptor at " + descriptor + " is invalid: "
                    + e.getMessage(), e);
        }
    }
}
