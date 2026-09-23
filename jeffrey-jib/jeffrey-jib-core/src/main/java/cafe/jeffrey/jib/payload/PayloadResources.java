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
