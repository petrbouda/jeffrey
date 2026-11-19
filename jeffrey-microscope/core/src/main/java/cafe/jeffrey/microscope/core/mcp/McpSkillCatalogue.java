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

import cafe.jeffrey.microscope.mcp.protocol.McpErrorCode;
import cafe.jeffrey.microscope.mcp.protocol.McpProtocolException;
import cafe.jeffrey.microscope.mcp.protocol.McpResource;
import cafe.jeffrey.microscope.mcp.protocol.McpSkill;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillFile;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The {@code microscope} plugin's skills, loaded once from the classpath copy the build makes under
 * {@code mcp-skills/}, and served two ways: over the {@code io.modelcontextprotocol/skills} extension
 * (this class is its {@link McpSkillProvider}) and, through {@link McpPromptRegistry}, as MCP prompts.
 * <p>
 * Every file is read as raw bytes, because the digest a host verifies a read against is computed over
 * exactly those bytes. A skill is served only when it passes the checks the extension and the Agent
 * Skills format set — a {@code SKILL.md} whose frontmatter parses and carries a {@code name} equal to
 * its directory and a {@code description}, UTF-8 throughout with no byte-order mark (the files go out
 * as text), at most 512 files and 16 MiB — and one that fails is left out with a warning rather than
 * taking the others with it: the skills are guidance, not the tool surface.
 * <p>
 * Skills and each manifest are sorted by URI, so the answer is the same whatever order the classpath
 * scan visited a directory or a jar in.
 */
public final class McpSkillCatalogue implements McpSkillProvider {

    private static final Logger LOG = LoggerFactory.getLogger(McpSkillCatalogue.class);

    /** Where the build copies the skills: one directory per skill, every file under it. */
    private static final String CLASSPATH_ROOT = "mcp-skills/";

    private static final String CLASSPATH_PATTERN = "classpath*:" + CLASSPATH_ROOT + "*/**";

    /** Every {@code mcp-skills/} directory on the classpath: what a file's path is taken relative to. */
    private static final String CLASSPATH_ROOTS = "classpath*:" + CLASSPATH_ROOT;

    private static final char PATH_SEPARATOR = '/';
    private static final String DIRECTORY_SUFFIX = "/";
    private static final String PARENT_SEGMENT = "..";
    private static final String CURRENT_SEGMENT = ".";

    /** The extension's per-skill limits: what every conforming host is guaranteed to accept. */
    private static final int MAX_FILES = 512;
    private static final long MAX_TOTAL_BYTES = 16L * 1024 * 1024;

    /** The Agent Skills naming rule: lowercase letters and digits in hyphen-separated runs, at most 64. */
    private static final Pattern SKILL_NAME = Pattern.compile("^(?=.{1,64}$)[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";

    private static final char EXTENSION_SEPARATOR = '.';
    private static final String NO_EXTENSION = "";
    private static final String TEXT_PLAIN = "text/plain";
    private static final Map<String, String> MIME_TYPES_BY_EXTENSION = Map.of(
            "md", McpResource.TEXT_MARKDOWN,
            "json", McpResource.APPLICATION_JSON,
            "txt", TEXT_PLAIN);

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * One served skill with the text after its frontmatter: the entry the extension serves and the body
     * a prompt is built from, so both come from the same parse of the same bytes.
     *
     * @param skill the entry, with its complete manifest
     * @param body  the {@code SKILL.md} text after the frontmatter
     */
    public record SkillText(McpSkill skill, String body) {

        public SkillText {
            if (skill == null || body == null) {
                throw new IllegalArgumentException("A skill text needs its skill and its body");
            }
        }
    }

    private final List<SkillText> texts;
    private final Map<String, McpSkill> skillsByUri;
    private final Map<String, McpSkillFile> filesByUri;

    /**
     * @param files every file, keyed by its path under {@code mcp-skills/}: {@code <skill>/<relative path>}
     */
    public McpSkillCatalogue(Map<String, byte[]> files) {
        Map<String, Map<String, byte[]>> bySkill = new TreeMap<>();
        files.forEach((path, bytes) -> {
            int separator = path.indexOf(PATH_SEPARATOR);
            if (separator <= 0 || separator == path.length() - 1) {
                LOG.warn("Skipped a file outside any MCP skill directory: path={}", path);
                return;
            }
            bySkill.computeIfAbsent(path.substring(0, separator), _ -> new TreeMap<>())
                    .put(path.substring(separator + 1), bytes);
        });

        List<SkillText> loaded = new ArrayList<>();
        bySkill.forEach((directory, skillFiles) -> load(directory, skillFiles).ifPresent(loaded::add));
        loaded.sort((left, right) -> left.skill().uri().compareTo(right.skill().uri()));

        Map<String, McpSkill> skills = new LinkedHashMap<>();
        Map<String, McpSkillFile> byUri = new LinkedHashMap<>();
        for (SkillText text : loaded) {
            skills.put(text.skill().uri(), text.skill());
            text.skill().files().forEach(file -> byUri.put(file.uri(), file));
        }
        this.texts = List.copyOf(loaded);
        this.skillsByUri = Collections.unmodifiableMap(skills);
        this.filesByUri = Collections.unmodifiableMap(byUri);
        LOG.debug("Loaded MCP skills: count={}", skills.size());
    }

    /**
     * The skills the build copied onto the classpath. A classpath that cannot be read serves none: a
     * Jeffrey without skills still serves every tool.
     */
    public static McpSkillCatalogue fromClasspath() {
        return fromClasspath(ClassUtils.getDefaultClassLoader());
    }

    /** The skills under {@code mcp-skills/} as this class loader sees them. */
    static McpSkillCatalogue fromClasspath(ClassLoader classLoader) {
        Map<String, byte[]> files = new TreeMap<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
            List<Path> directoryRoots = directoryRoots(resolver.getResources(CLASSPATH_ROOTS));
            for (Resource resource : resolver.getResources(CLASSPATH_PATTERN)) {
                String path = pathUnderRoot(resource, directoryRoots);
                if (path == null || !resource.isReadable()) {
                    continue;
                }
                if (files.putIfAbsent(path, read(resource)) != null) {
                    LOG.warn("Ignored a second copy of an MCP skill file on the classpath: path={}", path);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Could not read the MCP skills, so none will be served: message={}", e.getMessage());
            return new McpSkillCatalogue(Map.of());
        }
        return new McpSkillCatalogue(files);
    }

    @Override
    public List<McpSkill> skills() {
        return List.copyOf(skillsByUri.values());
    }

    @Override
    public McpSkill skill(String uri) {
        McpSkill skill = skillsByUri.get(uri);
        if (skill == null) {
            throw new McpProtocolException(McpErrorCode.INVALID_PARAMS, UNKNOWN_SKILL.formatted(uri));
        }
        return skill;
    }

    @Override
    public Optional<McpSkillFile> file(String uri) {
        return Optional.ofNullable(filesByUri.get(uri));
    }

    /** Every served skill with its body, sorted by URI: what the prompts are built from. */
    public List<SkillText> texts() {
        return texts;
    }

    /** The skill in one directory, or empty — with the reason logged — when it fails a check. */
    private static Optional<SkillText> load(String directory, Map<String, byte[]> files) {
        try {
            return Optional.of(validated(directory, files));
        } catch (IllegalArgumentException e) {
            LOG.warn("Skipped an MCP skill that cannot be served: skill={} reason={}", directory, e.getMessage());
            return Optional.empty();
        }
    }

    private static SkillText validated(String directory, Map<String, byte[]> files) {
        if (!SKILL_NAME.matcher(directory).matches()) {
            throw new IllegalArgumentException("the directory name is not a valid skill name");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("it has " + files.size() + " files; at most " + MAX_FILES
                    + " are served");
        }
        long total = files.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("its files hold " + total + " bytes; at most " + MAX_TOTAL_BYTES
                    + " are served");
        }
        byte[] skillMd = files.get(McpSkill.SKILL_FILE);
        if (skillMd == null) {
            throw new IllegalArgumentException("it has no " + McpSkill.SKILL_FILE);
        }
        String root = McpSkill.SCHEME + directory + PATH_SEPARATOR;
        List<McpSkillFile> manifest = new ArrayList<>();
        files.forEach((relative, bytes) -> {
            requireSafePath(relative);
            requireUtf8(relative, bytes);
            manifest.add(new McpSkillFile(root + relative, mimeType(relative), bytes));
        });

        SkillMarkdown markdown = SkillMarkdown.parse(new String(skillMd, StandardCharsets.UTF_8));
        ObjectNode frontmatter = markdown.frontmatter();
        String name = text(frontmatter, FIELD_NAME);
        if (!directory.equals(name)) {
            throw new IllegalArgumentException("its name (" + name + ") is not its directory");
        }
        String description = text(frontmatter, FIELD_DESCRIPTION);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("its frontmatter has no description");
        }
        return new SkillText(new McpSkill(root + McpSkill.SKILL_FILE, frontmatter, manifest), markdown.body());
    }

    /** A frontmatter value that is a string, or null for one that is missing or of another type. */
    private static String text(ObjectNode frontmatter, String field) {
        JsonNode value = frontmatter.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    /** A path that would leave the skill's directory could not be addressed under its URI. */
    private static void requireSafePath(String relative) {
        for (String segment : relative.split(String.valueOf(PATH_SEPARATOR), -1)) {
            if (segment.isEmpty() || PARENT_SEGMENT.equals(segment) || CURRENT_SEGMENT.equals(segment)) {
                throw new IllegalArgumentException("a file path is not a plain relative path: " + relative);
            }
        }
    }

    /**
     * Served as UTF-8 text, so the bytes must decode and re-encode to themselves — invalid sequences and
     * a byte-order mark both break that, and a host hashing what it read would see a different digest.
     */
    private static void requireUtf8(String relative, byte[] bytes) {
        if (bytes.length >= UTF8_BOM.length
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            throw new IllegalArgumentException("a file starts with a byte-order mark: " + relative);
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("a file is not valid UTF-8: " + relative, e);
        }
    }

    private static String mimeType(String relative) {
        int dot = relative.lastIndexOf(EXTENSION_SEPARATOR);
        String extension = dot < 0 ? NO_EXTENSION : relative.substring(dot + 1);
        return MIME_TYPES_BY_EXTENSION.getOrDefault(extension, TEXT_PLAIN);
    }

    /**
     * The {@code mcp-skills/} roots that are directories on the file system. A root inside a jar needs
     * none: its files are named by their entry, which starts at the archive's own root.
     */
    private static List<Path> directoryRoots(Resource[] roots) {
        List<Path> directories = new ArrayList<>();
        for (Resource root : roots) {
            try {
                if (!isArchive(root) && root.isFile()) {
                    directories.add(root.getFile().toPath().toAbsolutePath().normalize());
                }
            } catch (IOException e) {
                LOG.warn("Skipped an MCP skills root that cannot be read: root={} message={}", root, e.getMessage());
            }
        }
        return directories;
    }

    /**
     * The resource's path under {@code mcp-skills/} ({@code <skill>/<relative path>}), or null for a
     * directory or anything outside a root. Taken relative to the root the file was found under, never
     * by searching the URL for the root's name, so a directory inside a skill that happens to be named
     * {@code mcp-skills} stays part of the path.
     * <ul>
     *     <li>In a jar — a plain one, or the nested jar a Spring Boot executable runs from — the entry
     *     name after the last {@code !/} starts at the archive's root, so it must start with
     *     {@code mcp-skills/}.</li>
     *     <li>On the file system, the file is relativised against the {@code mcp-skills/} directory
     *     that contains it.</li>
     * </ul>
     */
    private static String pathUnderRoot(Resource resource, List<Path> directoryRoots) throws IOException {
        if (isArchive(resource)) {
            return entryUnderRoot(resource.getURL().toString());
        }
        if (!resource.isFile()) {
            LOG.warn("Skipped an MCP skill file that is neither in a jar nor on disk: resource={}", resource);
            return null;
        }
        Path file = resource.getFile().toPath().toAbsolutePath().normalize();
        if (Files.isDirectory(file)) {
            return null;
        }
        return directoryRoots.stream()
                .filter(file::startsWith)
                .max(Comparator.comparingInt(Path::getNameCount))
                .map(root -> root.relativize(file).toString().replace(File.separatorChar, PATH_SEPARATOR))
                .orElse(null);
    }

    private static String entryUnderRoot(String url) {
        int separator = url.lastIndexOf(ResourceUtils.JAR_URL_SEPARATOR);
        String entry = url.substring(separator + ResourceUtils.JAR_URL_SEPARATOR.length());
        if (!entry.startsWith(CLASSPATH_ROOT) || entry.endsWith(DIRECTORY_SUFFIX)) {
            return null;
        }
        try {
            String path = URI.create(entry.substring(CLASSPATH_ROOT.length())).getPath();
            return path == null || path.isEmpty() ? null : path;
        } catch (IllegalArgumentException e) {
            LOG.warn("Skipped an MCP skill file whose classpath URL cannot be read: url={}", url);
            return null;
        }
    }

    private static boolean isArchive(Resource resource) throws IOException {
        return ResourceUtils.isJarURL(resource.getURL());
    }

    private static byte[] read(Resource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
