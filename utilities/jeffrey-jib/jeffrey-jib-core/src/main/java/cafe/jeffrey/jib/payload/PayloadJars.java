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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
 */
abstract class PayloadJars {

    private static final String PAYLOAD_ENTRY_PREFIX = "jeffrey-payload/";
    private static final String TEMP_DIR_PREFIX = "jeffrey-jib-payload-";
    private static final String PATH_SEPARATOR = "/";

    /**
     * Extracts the payload to a temporary file that is deleted when the build JVM exits.
     *
     * @throws PayloadResolutionException the jar holds no payload, or more than one — either way
     *                                    the payload build staged the wrong thing and the image
     *                                    would be wrong
     */
    static Path extractSingleEntry(Path payloadJar, ArtifactCoordinates coordinates)
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
            return extract(jar, payloadEntries.get(0), coordinates);
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

    private static Path extract(JarFile jar, JarEntry entry, ArtifactCoordinates coordinates)
            throws PayloadResolutionException {

        String fileName = entry.getName().substring(entry.getName().lastIndexOf(PATH_SEPARATOR) + 1);
        try (InputStream in = jar.getInputStream(entry)) {
            Path directory = Files.createTempDirectory(TEMP_DIR_PREFIX);
            directory.toFile().deleteOnExit();
            Path target = directory.resolve(fileName);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException e) {
            throw new PayloadResolutionException(coordinates, "the payload could not be unpacked", e);
        }
    }
}
