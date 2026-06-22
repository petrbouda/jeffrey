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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Read-only filesystem tools the AI uses to navigate a {@link ClonedRepository} the way an agentic
 * code assistant would: list directories, glob for files, read a file, and grep file contents. Every
 * path argument is resolved against {@link #root} and rejected if it escapes the checkout, so the model
 * can never read outside the cloned repository. There are deliberately no write, delete or execute
 * tools — recommendation generation only ever reads source.
 *
 * <p>Each method returns a plain string (the model's tool result) and fails soft with an
 * {@code "Error: …"} message rather than throwing, mirroring the {@code DuckDbMcpTools} convention.</p>
 */
public class RepoAnalysisTools {

    private static final Logger LOG = LoggerFactory.getLogger(RepoAnalysisTools.class);

    private static final int MAX_LISTING_ENTRIES = 500;
    private static final int MAX_GLOB_RESULTS = 300;
    private static final int MAX_GREP_HITS = 200;
    private static final int MAX_FILE_BYTES = 200_000;
    private static final int MAX_WALK_DEPTH = 50;

    private static final String GLOB_SYNTAX_PREFIX = "glob:";
    // Directories that never contain reviewable source but would bloat globs/greps.
    private static final Set<String> IGNORED_DIR_NAMES =
            Set.of(".git", "node_modules", "target", "build", "dist", ".idea", ".gradle");

    private final Path root;

    public RepoAnalysisTools(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Tool(description = "List the files and subdirectories directly inside a directory of the cloned "
            + "repository. Use the repository-relative path (e.g. '' or '.' for the root, "
            + "'src/main/java' for a subdirectory). Directories are suffixed with '/'.")
    public String listFiles(
            @ToolParam(description = "Repository-relative directory path; empty or '.' means the repository root")
            String dir) {
        Path target = resolveSafe(dir);
        if (target == null) {
            return "Error: Path escapes the repository or does not exist: " + dir;
        }
        if (!Files.isDirectory(target)) {
            return "Error: Not a directory: " + dir;
        }

        List<String> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(target)) {
            children.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .limit(MAX_LISTING_ENTRIES)
                    .forEach(child -> {
                        String name = child.getFileName().toString();
                        entries.add(Files.isDirectory(child) ? name + "/" : name);
                    });
        } catch (IOException e) {
            LOG.warn("Failed to list directory: dir={} message={}", dir, e.getMessage());
            return "Error: Failed to list directory: " + e.getMessage();
        }

        if (entries.isEmpty()) {
            return "(empty directory)";
        }
        return String.join("\n", entries);
    }

    @Tool(description = "Find files in the cloned repository whose repository-relative path matches a "
            + "glob pattern (e.g. '**/*.java', 'src/**/Order*.java'). Returns matching repository-relative "
            + "paths, one per line.")
    public String glob(
            @ToolParam(description = "Glob pattern matched against repository-relative paths, e.g. '**/*.java'")
            String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "Error: A glob pattern is required";
        }

        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher(GLOB_SYNTAX_PREFIX + pattern);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid glob pattern: " + pattern;
        }

        List<String> matches = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, MAX_WALK_DEPTH)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(path))
                    .map(root::relativize)
                    .filter(matcher::matches)
                    .map(Path::toString)
                    .sorted()
                    .limit(MAX_GLOB_RESULTS)
                    .forEach(matches::add);
        } catch (IOException e) {
            LOG.warn("Failed to glob repository: pattern={} message={}", pattern, e.getMessage());
            return "Error: Failed to glob repository: " + e.getMessage();
        }

        if (matches.isEmpty()) {
            return "(no files matched " + pattern + ")";
        }
        return String.join("\n", matches);
    }

    @Tool(description = "Read the full text of a single file in the cloned repository. Use the "
            + "repository-relative path. Large files are truncated.")
    public String readFile(
            @ToolParam(description = "Repository-relative file path, e.g. 'src/main/java/com/acme/Order.java'")
            String path) {
        Path target = resolveSafe(path);
        if (target == null) {
            return "Error: Path escapes the repository or does not exist: " + path;
        }
        if (!Files.isRegularFile(target)) {
            return "Error: Not a file: " + path;
        }

        try {
            long size = Files.size(target);
            byte[] bytes = Files.readAllBytes(target);
            String content = new String(
                    bytes, 0, (int) Math.min(size, MAX_FILE_BYTES), java.nio.charset.StandardCharsets.UTF_8);
            if (size > MAX_FILE_BYTES) {
                return content + "\n\n… (truncated at " + MAX_FILE_BYTES + " bytes of " + size + ")";
            }
            return content;
        } catch (IOException e) {
            LOG.warn("Failed to read file: path={} message={}", path, e.getMessage());
            return "Error: Failed to read file: " + e.getMessage();
        }
    }

    @Tool(description = "Search the cloned repository for lines containing a literal substring "
            + "(case-sensitive). Optionally restrict the search to files matching a glob. Returns "
            + "'path:lineNumber: line' hits.")
    public String grep(
            @ToolParam(description = "Literal substring to search for, e.g. a method or class name")
            String query,
            @ToolParam(description = "Optional glob to restrict which files are searched, e.g. '**/*.java'; empty searches all files")
            String pathGlob) {
        if (query == null || query.isBlank()) {
            return "Error: A search query is required";
        }

        PathMatcher matcher = null;
        if (pathGlob != null && !pathGlob.isBlank()) {
            try {
                matcher = FileSystems.getDefault().getPathMatcher(GLOB_SYNTAX_PREFIX + pathGlob);
            } catch (IllegalArgumentException e) {
                return "Error: Invalid glob pattern: " + pathGlob;
            }
        }

        List<String> hits = new ArrayList<>();
        PathMatcher fileFilter = matcher;
        try (Stream<Path> walk = Files.walk(root, MAX_WALK_DEPTH)) {
            Iterable<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(path))
                    .filter(path -> fileFilter == null || fileFilter.matches(root.relativize(path)))
                    ::iterator;
            for (Path file : files) {
                if (hits.size() >= MAX_GREP_HITS) {
                    break;
                }
                collectHits(file, query, hits);
            }
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Failed to grep repository: query_length={} message={}", query.length(), e.getMessage());
            return "Error: Failed to search repository: " + e.getMessage();
        }

        if (hits.isEmpty()) {
            return "(no matches)";
        }
        String body = String.join("\n", hits);
        if (hits.size() >= MAX_GREP_HITS) {
            return body + "\n\n… (stopped at " + MAX_GREP_HITS + " matches)";
        }
        return body;
    }

    private void collectHits(Path file, String query, List<String> hits) {
        String relative = root.relativize(file).toString();
        try {
            List<String> lines = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(query)) {
                    hits.add(relative + ":" + (i + 1) + ": " + lines.get(i).strip());
                    if (hits.size() >= MAX_GREP_HITS) {
                        return;
                    }
                }
            }
        } catch (IOException e) {
            // Binary or unreadable file — skip silently, it is not reviewable source anyway.
        }
    }

    /**
     * Resolves a repository-relative path and returns it only if it stays within the checkout and
     * exists; otherwise {@code null}. Blocks both {@code ../} traversal and absolute paths.
     */
    private Path resolveSafe(String relative) {
        String cleaned = (relative == null || relative.isBlank() || relative.equals(".")) ? "" : relative;
        Path resolved = root.resolve(cleaned).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        if (!Files.exists(resolved)) {
            return null;
        }
        return resolved;
    }

    private boolean isIgnored(Path path) {
        for (Path segment : root.relativize(path)) {
            if (IGNORED_DIR_NAMES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
