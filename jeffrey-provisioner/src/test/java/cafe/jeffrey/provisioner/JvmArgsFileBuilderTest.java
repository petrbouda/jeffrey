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

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmArgsFileBuilderTest {

    private static final Path SESSION_PATH = Path.of("/workspaces/ws/project/instance/session");

    private static List<String> optionLines(String content) {
        return content.lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
    }

    @Test
    void writesOneOptionPerLine() {
        String content = new JvmArgsFileBuilder()
                .build(new JvmArgsFileBuilder.Context(SESSION_PATH, "-Xmx1200m -XX:+UseG1GC -XX:+AlwaysPreTouch"));

        assertEquals(List.of("-Xmx1200m", "-XX:+UseG1GC", "-XX:+AlwaysPreTouch"), optionLines(content));
    }

    @Test
    void headsTheFileWithTheSessionPath() {
        String content = new JvmArgsFileBuilder()
                .build(new JvmArgsFileBuilder.Context(SESSION_PATH, "-Xmx1g"));

        assertTrue(content.startsWith("# Jeffrey JVM arguments"), content);
        assertTrue(content.contains("# Session: " + SESSION_PATH), content);
        assertTrue(content.endsWith("\n"), "argfile must end with a newline");
    }

    /**
     * A quoted option carrying whitespace must stay on one line — split across two, the JVM
     * rejects the argfile and the application never starts.
     */
    @Test
    void keepsAQuotedOptionWithWhitespaceOnOneLine() {
        String content = new JvmArgsFileBuilder().build(new JvmArgsFileBuilder.Context(
                SESSION_PATH, "-Xmx1g -Djeffrey.logging.path=\"/opt/my app/jeffrey.log\" -XX:+UseG1GC"));

        assertEquals(
                List.of("-Xmx1g", "\"-Djeffrey.logging.path=/opt/my app/jeffrey.log\"", "-XX:+UseG1GC"),
                optionLines(content));
    }

    @Test
    void returnsEmptyContentWithoutProfilerSettings() {
        assertEquals("", new JvmArgsFileBuilder().build(new JvmArgsFileBuilder.Context(SESSION_PATH, null)));
        assertEquals("", new JvmArgsFileBuilder().build(new JvmArgsFileBuilder.Context(SESSION_PATH, "   ")));
    }
}
