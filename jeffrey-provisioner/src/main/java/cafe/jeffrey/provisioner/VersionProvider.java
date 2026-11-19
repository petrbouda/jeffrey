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

package cafe.jeffrey.provisioner;

import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class VersionProvider implements CommandLine.IVersionProvider {

    public static final String[] NOT_AVAILABLE = {"not-available"};

    /** Written into the jar by the build: the release tag, or nothing in a development build. */
    private static final String VERSION_RESOURCE = "jeffrey-tag.txt";

    @Override
    public String[] getVersion() {
        try {
            String version = readVersion();
            if (version.isBlank()) {
                return NOT_AVAILABLE;
            } else {
                return new String[]{version};
            }
        } catch (Exception ex) {
            return NOT_AVAILABLE;
        }
    }

    private static String readVersion() throws IOException {
        try (InputStream stream = VersionProvider.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Not on the classpath: " + VERSION_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
