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

package cafe.jeffrey.microscope.mcp.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The protocol module holds the MCP protocol and nothing of Jeffrey's: no {@code cafe.jeffrey.*} type
 * outside its own package, and no Spring. Maven already makes this so — the pom names only Jackson and
 * SLF4J — but a pom is edited once and read never, and this is the sentence that fails when someone
 * adds a dependency and an import to go with it.
 */
class ModuleBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java").toAbsolutePath().normalize();

    private static final String OWN_PACKAGE = "cafe.jeffrey.microscope.mcp.protocol";

    private static final Pattern IMPORT = Pattern.compile("^\\s*import (?:static )?([\\w.]+)");

    private static final Pattern REQUIRES = Pattern.compile("^\\s*requires (?:transitive |static )*([\\w.]+);");

    /** The modules the protocol may read: Jackson for the wire, SLF4J for the log. */
    private static final Set<String> ALLOWED_MODULES =
            Set.of("tools.jackson.databind", "com.fasterxml.jackson.annotation", "org.slf4j");

    @Test
    void noMainSourceImportsJeffreyOrSpring() throws IOException {
        assertEquals(List.of(), offenders(MAIN_SOURCES));
    }

    @Test
    void theModuleReadsOnlyJacksonAndSlf4j() throws IOException {
        List<String> required;
        try (Stream<String> lines = Files.lines(MAIN_SOURCES.resolve("module-info.java"))) {
            required = lines.map(REQUIRES::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .filter(module -> !ALLOWED_MODULES.contains(module))
                    .toList();
        }
        assertEquals(List.of(), required);
    }

    /** The scan itself: a source that reaches for Jeffrey or Spring is named, one in the package is not. */
    @Test
    void theScanNamesEveryForbiddenImport(@TempDir Path sources) throws IOException {
        write(sources, "Common.java", "import cafe.jeffrey.shared.common.Json;");
        write(sources, "Adapter.java", "import static cafe.jeffrey.profile.mcp.McpToolOutput.capped;");
        write(sources, "Spring.java", "import org.springframework.http.ResponseEntity;");
        write(sources, "Own.java", "import " + OWN_PACKAGE + ".McpDispatcher;");
        write(sources, "Jackson.java", "import tools.jackson.databind.JsonNode;");
        write(sources, "Indented.java", "    import org.springframework.http.ResponseEntity;");

        assertEquals(List.of("Adapter.java", "Common.java", "Indented.java", "Spring.java"), offenders(sources));
    }

    private static List<String> offenders(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ModuleBoundaryTest::importsOutsideTheProtocol)
                    .map(root::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }

    private static boolean importsOutsideTheProtocol(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.map(IMPORT::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .anyMatch(ModuleBoundaryTest::forbidden);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }

    private static boolean forbidden(String imported) {
        boolean jeffrey = imported.startsWith("cafe.jeffrey.") && !imported.startsWith(OWN_PACKAGE + ".");
        return jeffrey || imported.startsWith("org.springframework.");
    }

    private static void write(Path root, String name, String line) throws IOException {
        Files.writeString(root.resolve(name), "package example;\n\n" + line + "\n\nclass X {\n}\n");
    }
}
