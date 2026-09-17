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

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * How one kind of payload is named, both in the image and on the wire.
 *
 * <p>Two shapes exist and they differ in exactly one respect — whether the file depends on the
 * target architecture — so the difference is a type rather than a flag consulted at four call
 * sites.
 *
 * <p>The naming rule lives here and nowhere else. A single-platform image, which is the common
 * case, gets unsuffixed file names and a literal path baked into its environment variable. A
 * multi-platform image cannot: Jib layers are not per-platform, so every architecture's file ships
 * in every manifest of the index and the names have to be distinct. Those images bake a path
 * containing {@link #ARCH_PLACEHOLDER}, which {@code jeffrey-entrypoint.sh} expands from
 * {@code uname -m} at container start.
 */
public sealed interface PayloadSpec permits PayloadSpec.ArchScoped, PayloadSpec.Neutral {

    /** The Maven namespace every payload is published under, matching the extension's own. */
    String PAYLOAD_GROUP_ID = "cafe.jeffrey-analyst";

    /** Where payloads land in the image. Owned by Jeffrey, so nothing in the base image collides. */
    AbsoluteUnixPath INSTALL_DIR = AbsoluteUnixPath.get("/opt/jeffrey");

    /** Expanded by the entrypoint script at container start; only multi-platform images bake it. */
    String ARCH_PLACEHOLDER = "{arch}";

    String ARCH_SEPARATOR = "-";

    /** Payload classifiers are {@code linux-<arch>}; Jeffrey profiles Linux containers only. */
    String CLASSIFIER_OS_PREFIX = "linux-";

    /** The artifacts to fetch and where each one is installed. */
    List<PayloadRequest> requestsFor(Set<String> architectures, String version);

    /** The value baked into this payload's {@code JEFFREY_*} environment variable. */
    String envPath(Set<String> architectures);

    /** A payload that exists once per architecture: the native provisioner, and async-profiler. */
    record ArchScoped(String artifactId, String baseName, String extension, FilePermissions permissions)
            implements PayloadSpec {

        @Override
        public List<PayloadRequest> requestsFor(Set<String> architectures, String version) {
            boolean multiArch = architectures.size() > 1;
            List<PayloadRequest> requests = new ArrayList<>(architectures.size());
            for (String architecture : architectures) {
                ArtifactCoordinates coordinates = new ArtifactCoordinates(
                        PAYLOAD_GROUP_ID, artifactId, CLASSIFIER_OS_PREFIX + architecture, null, version);
                String fileName = multiArch
                        ? baseName + ARCH_SEPARATOR + architecture + extension
                        : baseName + extension;
                requests.add(new PayloadRequest(
                        coordinates, INSTALL_DIR.resolve(fileName), permissions));
            }
            return List.copyOf(requests);
        }

        @Override
        public String envPath(Set<String> architectures) {
            String fileName = architectures.size() > 1
                    ? baseName + ARCH_SEPARATOR + ARCH_PLACEHOLDER + extension
                    : baseName + extension;
            return INSTALL_DIR.resolve(fileName).toString();
        }
    }

    /** A payload that is the same file on every architecture: the provisioner jar. */
    record Neutral(String artifactId, String baseName, String extension, FilePermissions permissions)
            implements PayloadSpec {

        @Override
        public List<PayloadRequest> requestsFor(Set<String> architectures, String version) {
            ArtifactCoordinates coordinates =
                    new ArtifactCoordinates(PAYLOAD_GROUP_ID, artifactId, null, null, version);
            return List.of(new PayloadRequest(
                    coordinates, INSTALL_DIR.resolve(baseName + extension), permissions));
        }

        @Override
        public String envPath(Set<String> architectures) {
            return INSTALL_DIR.resolve(baseName + extension).toString();
        }
    }
}
