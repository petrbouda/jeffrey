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

/**
 * Maven coordinates of one payload artifact, in the form both build systems can consume.
 *
 * <p>Payloads are ordinary Maven artifacts published beside the extension, so the same record
 * feeds Aether on Maven and a detached configuration on Gradle.
 *
 * @param groupId    always {@link PayloadSpec#PAYLOAD_GROUP_ID} in practice, but kept explicit
 * @param artifactId the payload module
 * @param classifier {@code linux-amd64} / {@code linux-arm64}, or empty for an arch-neutral payload
 * @param extension  the packaging; payloads are jars carrying a single file
 * @param version    the payload version, which the consumer states explicitly via
 *                   {@code payloadVersion} — it tracks the Jeffrey release the binaries came from
 */
public record ArtifactCoordinates(
        String groupId, String artifactId, String classifier, String extension, String version) {

    private static final String DEFAULT_EXTENSION = "jar";
    private static final String NO_CLASSIFIER = "";
    private static final String COORDINATE_SEPARATOR = ":";

    public ArtifactCoordinates {
        requireText(groupId, "groupId");
        requireText(artifactId, "artifactId");
        requireText(version, "version");
        if (classifier == null) {
            classifier = NO_CLASSIFIER;
        }
        if (extension == null || extension.isBlank()) {
            extension = DEFAULT_EXTENSION;
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payload coordinate '" + field + "' must not be blank");
        }
    }

    /** Whether this artifact is published under a classifier. */
    public boolean hasClassifier() {
        return !classifier.isEmpty();
    }

    /**
     * Gradle dependency notation: {@code group:name:version} or {@code group:name:version:classifier}.
     * Gradle's {@code DependencyHandler.create(Object)} parses this string form, which keeps the
     * reflective Gradle resolver free of any typed Gradle API.
     */
    public String gradleNotation() {
        String notation = groupId + COORDINATE_SEPARATOR + artifactId + COORDINATE_SEPARATOR + version;
        if (hasClassifier()) {
            return notation + COORDINATE_SEPARATOR + classifier;
        }
        return notation;
    }

    @Override
    public String toString() {
        if (hasClassifier()) {
            return groupId + COORDINATE_SEPARATOR + artifactId + COORDINATE_SEPARATOR + classifier
                    + COORDINATE_SEPARATOR + version;
        }
        return groupId + COORDINATE_SEPARATOR + artifactId + COORDINATE_SEPARATOR + version;
    }
}
