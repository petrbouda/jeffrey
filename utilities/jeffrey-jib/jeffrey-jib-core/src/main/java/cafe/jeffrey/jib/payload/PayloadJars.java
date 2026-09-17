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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.CRC32;

/**
 * Unpacks the one file a payload jar carries.
 *
 * <p>Payloads travel as ordinary jars so that Maven Central, Aether and Gradle all handle them
 * without special cases, but what is inside is a native binary or a nested jar. Each payload jar
 * holds exactly one file under {@value #PAYLOAD_ENTRY_PREFIX}; finding it by prefix rather than by
 * name means the extension does not have to agree with the payload build about a file name.
 *
 * <p>The prefix is deliberately not a Java package name. Maven Central requires every artifact to
 * ship sources and javadoc, so each payload module carries a holder class — and if that class lived
 * under the same prefix the payload does, it would count as a second payload and fail every build.
 *
 * <p>Files are unpacked to a path derived from the artifact coordinates, and a file that is already
 * there with the right size and checksum is left untouched. JIB keys its layer cache on each
 * source file's path and modification time, so a payload that lands at a new random path on every
 * build would be re-archived and re-hashed every time — tens of megabytes of wasted work per build.
 */
abstract class PayloadJars {

    /** Manifest attribute naming the release a payload's binary was taken from. */
    static final String PROVENANCE_ATTRIBUTE = "Jeffrey-Payload-Provenance";

    private static final String PAYLOAD_ENTRY_PREFIX = "jeffrey-payload/";
    private static final String PATH_SEPARATOR = "/";
    private static final String NAME_SEPARATOR = "-";
    private static final long UNKNOWN = -1;

    /**
     * Unpacks the payload into {@code directory}, reusing a previous build's copy when it matches.
     *
     * @throws PayloadResolutionException the jar holds no payload, or more than one — either way
     *                                    the payload build staged the wrong thing and the image
     *                                    would be wrong
     */
    static UnpackedPayload unpack(Path payloadJar, ArtifactCoordinates coordinates, Path directory)
            throws PayloadResolutionException {

        try (JarFile jar = new JarFile(payloadJar.toFile())) {
            List<JarEntry> payloadEntries = payloadEntries(jar);
            if (payloadEntries.isEmpty()) {
                throw new PayloadResolutionException(coordinates,
                        "the payload jar carries no file under " + PAYLOAD_ENTRY_PREFIX
                                + " (was the binary staged before the payload was published?)");
            }
            if (payloadEntries.size() > 1) {
                throw new PayloadResolutionException(coordinates,
                        "the payload jar carries " + payloadEntries.size() + " files under "
                                + PAYLOAD_ENTRY_PREFIX + ", expected exactly one: " + payloadEntries);
            }
            JarEntry entry = payloadEntries.get(0);
            Path target = targetFor(directory, coordinates, entry);
            if (!isUpToDate(target, entry)) {
                extract(jar, entry, target);
            }
            return new UnpackedPayload(target, provenance(jar));
        } catch (IOException e) {
            throw new PayloadResolutionException(coordinates, "the payload jar could not be read", e);
        }
    }

    private static List<JarEntry> payloadEntries(JarFile jar) {
        List<JarEntry> entries = new ArrayList<>();
        Enumeration<JarEntry> candidates = jar.entries();
        while (candidates.hasMoreElements()) {
            JarEntry entry = candidates.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(PAYLOAD_ENTRY_PREFIX)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** {@code <directory>/<artifactId>-<version>[-<classifier>]/<file name inside the jar>}. */
    private static Path targetFor(Path directory, ArtifactCoordinates coordinates, JarEntry entry) {
        String fileName = entry.getName().substring(entry.getName().lastIndexOf(PATH_SEPARATOR) + 1);
        String folder = coordinates.artifactId() + NAME_SEPARATOR + coordinates.version();
        if (coordinates.hasClassifier()) {
            folder = folder + NAME_SEPARATOR + coordinates.classifier();
        }
        return directory.resolve(folder).resolve(fileName);
    }

    /**
     * Whether a previous unpack left the same bytes behind. Size is checked first because it is
     * free; the CRC the jar already carries then confirms the content without trusting timestamps.
     */
    private static boolean isUpToDate(Path target, JarEntry entry) throws IOException {
        if (!Files.isRegularFile(target)) {
            return false;
        }
        if (entry.getSize() != UNKNOWN && Files.size(target) != entry.getSize()) {
            return false;
        }
        if (entry.getCrc() == UNKNOWN) {
            return false;
        }
        return crc32(target) == entry.getCrc();
    }

    private static long crc32(Path file) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(Files.readAllBytes(file));
        return crc.getValue();
    }

    private static void extract(JarFile jar, JarEntry entry, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = jar.getInputStream(entry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Optional<String> provenance(JarFile jar) throws IOException {
        Manifest manifest = jar.getManifest();
        if (manifest == null) {
            return Optional.empty();
        }
        String value = manifest.getMainAttributes().getValue(PROVENANCE_ATTRIBUTE);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
