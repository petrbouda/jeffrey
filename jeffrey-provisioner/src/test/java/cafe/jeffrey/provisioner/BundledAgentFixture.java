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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link JeffreyAgentInstaller} over a stand-in agent jar. The real one is bundled only when the
 * provisioner is packaged, which is after its tests run.
 */
final class BundledAgentFixture {

    static final String RESOURCE = "bundled/jeffrey-agent.jar";
    static final byte[] CONTENT = {'P', 'K', 3, 4, 'a', 'g', 'e', 'n', 't'};

    private BundledAgentFixture() {
    }

    /** An installer whose bundled agent is {@link #CONTENT}, laid out under {@code root}. */
    static JeffreyAgentInstaller bundledUnder(Path root) {
        try {
            Path jar = root.resolve(RESOURCE);
            Files.createDirectories(jar.getParent());
            Files.write(jar, CONTENT);
            return new JeffreyAgentInstaller(classLoaderOver(root), RESOURCE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An installer built like one from a provisioner packaged without the agent. */
    static JeffreyAgentInstaller missingUnder(Path root) {
        return new JeffreyAgentInstaller(classLoaderOver(root), RESOURCE);
    }

    private static ClassLoader classLoaderOver(Path root) {
        try {
            return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
