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
