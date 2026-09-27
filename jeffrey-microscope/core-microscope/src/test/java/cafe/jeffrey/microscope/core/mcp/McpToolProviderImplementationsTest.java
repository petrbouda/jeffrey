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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three shapes of toolset are the whole design: a fixed target ({@link ReflectiveToolset}), a target
 * resolved per call from a profile id ({@link ProfileScopedToolset}), and the union of several families
 * ({@link CompositeToolset}). {@link McpToolProvider} used to be sealed to say so; it lives in the protocol
 * module now and a sealed interface cannot permit a class of another module, so this test holds the line
 * instead. Anything else belongs in a {@code @Tool} class rather than in a fourth kind of provider.
 * <p>
 * It reads every main class of Jeffrey's on the test classpath — the class directories and jars of the
 * reactor, not the test classes or test-jars, whose stubs are fixtures — and loads only the ones whose
 * bytes name the interface.
 */
class McpToolProviderImplementationsTest {

    private static final Set<String> ALLOWED = Set.of(
            ReflectiveToolset.class.getName(),
            ProfileScopedToolset.class.getName(),
            CompositeToolset.class.getName());

    private static final String JEFFREY_CLASSES = "cafe/jeffrey/";
    private static final String CLASS_SUFFIX = ".class";
    private static final String MAIN_CLASSES_DIRECTORY = "target" + File.separator + "classes";
    private static final String JAR_SUFFIX = ".jar";
    private static final String TEST_JAR_SUFFIX = "-tests.jar";

    /** How the interface is spelled in a class file's constant pool. */
    private static final String INTERFACE_NAME = McpToolProvider.class.getName().replace('.', '/');

    /** Surefire's test classpath; the JVM's own is a manifest-only booter jar when forked. */
    private static final String SUREFIRE_CLASSPATH = "surefire.test.class.path";
    private static final String JVM_CLASSPATH = "java.class.path";

    @Test
    void onlyTheThreeToolsetShapesImplementTheProvider() {
        assertEquals(new TreeSet<>(ALLOWED), implementations());
    }

    private static Set<String> implementations() {
        Set<String> found = new TreeSet<>();
        for (String entry : System.getProperty(SUREFIRE_CLASSPATH, System.getProperty(JVM_CLASSPATH)).split(File.pathSeparator)) {
            Path path = Path.of(entry);
            List<String> candidates = mainClasspathEntry(path) ? namingTheInterface(path) : List.of();
            for (String className : candidates) {
                Class<?> type = load(className);
                if (!type.isInterface() && McpToolProvider.class.isAssignableFrom(type)) {
                    found.add(type.getName());
                }
            }
        }
        return found;
    }

    private static boolean mainClasspathEntry(Path path) {
        String name = path.toString();
        if (Files.isDirectory(path)) {
            return name.endsWith(MAIN_CLASSES_DIRECTORY);
        }
        return name.endsWith(JAR_SUFFIX) && !name.endsWith(TEST_JAR_SUFFIX);
    }

    /** The Jeffrey classes in one classpath entry whose bytes mention {@link McpToolProvider}. */
    private static List<String> namingTheInterface(Path entry) {
        List<String> names = new ArrayList<>();
        try {
            if (Files.isDirectory(entry)) {
                try (Stream<Path> files = Files.walk(entry)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String relative = entry.relativize(file).toString().replace(File.separatorChar, '/');
                        if (isJeffreyClass(relative) && mentionsTheInterface(Files.readAllBytes(file))) {
                            names.add(className(relative));
                        }
                    }
                }
            } else {
                try (JarFile jar = new JarFile(entry.toFile())) {
                    for (JarEntry file : jar.stream().toList()) {
                        if (isJeffreyClass(file.getName())) {
                            try (InputStream in = jar.getInputStream(file)) {
                                if (mentionsTheInterface(in.readAllBytes())) {
                                    names.add(className(file.getName()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the classpath entry " + entry, e);
        }
        return names;
    }

    private static boolean isJeffreyClass(String path) {
        return path.startsWith(JEFFREY_CLASSES) && path.endsWith(CLASS_SUFFIX);
    }

    private static String className(String path) {
        return path.substring(0, path.length() - CLASS_SUFFIX.length()).replace('/', '.');
    }

    /** A class file names its interfaces in its constant pool, as plain modified-UTF-8 bytes. */
    private static boolean mentionsTheInterface(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(INTERFACE_NAME);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, McpToolProviderImplementationsTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("A class on the classpath cannot be loaded: " + className, e);
        }
    }
}
