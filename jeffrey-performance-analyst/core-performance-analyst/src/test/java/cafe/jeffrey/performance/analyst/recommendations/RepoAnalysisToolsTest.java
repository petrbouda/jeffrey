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

package cafe.jeffrey.performance.analyst.recommendations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoAnalysisToolsTest {

    @TempDir
    Path repo;

    private RepoAnalysisTools tools;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(repo.resolve("src/main/java/com/acme"));
        Files.writeString(repo.resolve("src/main/java/com/acme/Order.java"),
                "package com.acme;\nclass Order {\n  void recompute() { hotLoop(); }\n}\n");
        Files.writeString(repo.resolve("README.md"), "# Acme\nrecompute notes\n");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/config"), "secret-internal");
        tools = new RepoAnalysisTools(repo);
    }

    @Nested
    class ReadFile {

        @Test
        void readsRepositoryRelativeFile() {
            String content = tools.readFile("src/main/java/com/acme/Order.java");
            assertTrue(content.contains("void recompute()"));
        }

        @Test
        void rejectsParentTraversal() {
            String result = tools.readFile("../outside.txt");
            assertTrue(result.startsWith("Error:"), result);
        }

        @Test
        void rejectsAbsolutePathOutsideRepo() {
            String result = tools.readFile("/etc/passwd");
            assertTrue(result.startsWith("Error:"), result);
        }

        @Test
        void reportsMissingFile() {
            String result = tools.readFile("src/main/java/com/acme/Missing.java");
            assertTrue(result.startsWith("Error:"), result);
        }
    }

    @Nested
    class Glob {

        @Test
        void matchesJavaFiles() {
            String result = tools.glob("**/*.java");
            assertTrue(result.contains("src/main/java/com/acme/Order.java"), result);
        }

        @Test
        void skipsIgnoredDirectories() {
            String result = tools.glob("**/config");
            assertFalse(result.contains(".git/config"), result);
        }

        @Test
        void reportsNoMatches() {
            String result = tools.glob("**/*.kt");
            assertTrue(result.startsWith("(no files matched"), result);
        }
    }

    @Nested
    class Grep {

        @Test
        void findsLiteralAcrossFiles() {
            String result = tools.grep("recompute", null);
            assertTrue(result.contains("Order.java:"), result);
            assertTrue(result.contains("README.md:"), result);
        }

        @Test
        void restrictsByGlob() {
            String result = tools.grep("recompute", "**/*.java");
            assertTrue(result.contains("Order.java:"), result);
            assertFalse(result.contains("README.md:"), result);
        }

        @Test
        void doesNotSearchIgnoredDirectories() {
            String result = tools.grep("secret-internal", null);
            assertEquals("(no matches)", result);
        }
    }

    @Nested
    class ListFiles {

        @Test
        void listsRootWithDirectorySuffix() {
            String result = tools.listFiles("");
            assertTrue(result.contains("src/"), result);
            assertTrue(result.contains("README.md"), result);
        }

        @Test
        void rejectsTraversal() {
            String result = tools.listFiles("../");
            assertTrue(result.startsWith("Error:"), result);
        }
    }
}
