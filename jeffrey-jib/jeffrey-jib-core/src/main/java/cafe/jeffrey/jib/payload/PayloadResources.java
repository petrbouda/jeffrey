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
import java.util.Arrays;

/**
 * Copies one payload file out of the payload jar on the class path.
 *
 * <p>Files are unpacked to a path derived from the resource name, and a file that is already there
 * with the same bytes is left untouched. JIB keys its layer cache on each source file's path and
 * modification time, so a payload rewritten on every build would be re-archived and re-hashed every
 * time — tens of megabytes of wasted work per build.
 */
abstract class PayloadResources {

    private static final String UNPACK_DIRECTORY = "payload";
    private static final String PATH_SEPARATOR = "/";

    /**
     * Unpacks the resource into {@code directory}, reusing a previous build's copy when it matches.
     *
     * @throws PayloadResolutionException the payload jar carries no such file — it was published
     *                                    without the binary staged, and the image would be wrong
     */
    static Path unpack(ClassLoader classLoader, String resource, Path directory) throws PayloadResolutionException {
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new PayloadResolutionException(resource,
                        "the payload jar carries no such file (was the binary staged before the payload was published?)");
            }
            byte[] content = in.readAllBytes();
            Path target = directory.resolve(UNPACK_DIRECTORY).resolve(fileName(resource));
            if (!isUpToDate(target, content)) {
                Files.createDirectories(target.getParent());
                Files.write(target, content);
            }
            return target;
        } catch (IOException e) {
            throw new PayloadResolutionException(resource, "the payload could not be unpacked", e);
        }
    }

    private static String fileName(String resource) {
        return resource.substring(resource.lastIndexOf(PATH_SEPARATOR) + 1);
    }

    /** Size first because it is free; the bytes then confirm the content without trusting timestamps. */
    private static boolean isUpToDate(Path target, byte[] content) throws IOException {
        if (!Files.isRegularFile(target) || Files.size(target) != content.length) {
            return false;
        }
        return Arrays.equals(Files.readAllBytes(target), content);
    }
}
