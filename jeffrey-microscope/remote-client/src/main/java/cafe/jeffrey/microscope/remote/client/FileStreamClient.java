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

package cafe.jeffrey.microscope.remote.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Pulls one file of a session onto this machine, whatever that file is. The source looks the file
 * up and streams the bytes without interpreting them; what the file is decides what the caller
 * does with it afterwards, and the caller read that off the listing that gave it this id.
 */
public interface FileStreamClient {

    @FunctionalInterface
    interface InputStreamConsumer {
        void accept(InputStream inputStream, TransferredFile file) throws IOException;
    }

    /**
     * What the hub said it was sending, before a byte of it is written.
     *
     * @param name the name the file has on the hub — which is the name the receiver writes, not
     *             the one it asked for. A caller's listing is older than its request, and the hub
     *             may have renamed the file since; written under the remembered name, the bytes
     *             land as something a reader going by the name reads wrongly.
     * @param size the file's size, or {@link #UNKNOWN_CONTENT_LENGTH} when the hub sent none
     */
    record TransferredFile(String name, long size) {

        /**
         * Reduced to a single path element here rather than at each place that resolves it into a
         * directory: the name comes off the wire, and one guard that cannot be forgotten beats
         * three that can.
         */
        public TransferredFile {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException(
                        "The hub sent a file with no name; there is nothing to write it as.");
            }
            name = Path.of(name).getFileName().toString();
            if (name.isBlank() || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException(
                        "Refusing a transferred file name that is not a single path element");
            }
        }
    }

    /**
     * Streams one file of a session to the consumer, named as it lies now.
     *
     * @throws RuntimeException carrying the source's reason when it will not hand the file over
     */
    void streamFile(ProjectScope scope, String sessionId, String fileId, InputStreamConsumer consumer);
}
