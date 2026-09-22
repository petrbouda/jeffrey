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


package cafe.jeffrey.shared.common.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The on-disk contract of hub-published configuration on the shared volume.
 *
 * <p>The hub (writer) renders the values stored at a {@link ConfigScope} into one HOCON file in the
 * folder that belongs to that scope; the provisioner (reader) merges whichever of the three files
 * exist as configuration layers, in {@link ConfigScope} order, between the image-baked base config
 * and the container's own override. The hub database is the source of truth; the file is a
 * projection, always overwritten whole through a temporary file and a rename, never appended and
 * never versioned by name. A scope holding no values has no file.</p>
 *
 * <pre>
 * &lt;workspaces&gt;/
 *   .config/jeffrey.conf                  GLOBAL
 *   &lt;workspace-ref-id&gt;/
 *     .config/jeffrey.conf                WORKSPACE
 *     &lt;project-name&gt;/
 *       .config/jeffrey.conf              PROJECT
 * </pre>
 *
 * <p>A published file only ever sets the paths of {@link ConfigType}; the hub renders it from typed
 * values and never from text a user wrote. {@link #publishablePaths()} is the reader's side of
 * that: the provisioner drops every other key from a volume layer with a warning, so even a file
 * written by a future hub, or edited by hand on the volume, cannot relocate a JVM's session tree,
 * rename its project or add a JVM flag that no type authorises.</p>
 *
 * <p>An absent file means "nothing at this scope". An unreadable file is a skipped layer, never a
 * failed provisioning: losing central configuration costs the defaults, not profiling.</p>
 */
public abstract class ScopedConfigLayout {

    private ScopedConfigLayout() {
    }

    /** Directory, inside the folder of a scope, that holds the published file */
    public static final String CONFIG_DIR = ".config";

    /** The published file's name; HOCON, UTF-8 */
    public static final String CONFIG_FILE = "jeffrey.conf";

    /**
     * Suffix of the temporary file a rendered file is written through before the rename. Not
     * {@code .conf}, so a reader never mistakes a half-written file for a published one.
     */
    public static final String TEMP_SUFFIX = ".tmp";

    private static final char NAME_WORD_SEPARATOR = '_';
    private static final char PATH_WORD_SEPARATOR = '-';

    /**
     * The HOCON path a type renders to: its {@link ConfigType} name, lower-cased with underscores
     * as dashes. The provisioner's own key is spelled to match ({@code ConfigPaths}), so a
     * published value and a locally configured one are the same setting, merge by the ordinary
     * rules, and nothing maps one vocabulary onto the other. One name, derived twice.
     *
     * <p>The convention costs nesting: a type's key is always top level, so a future setting whose
     * provisioner key would contain a dot is given a flat key rather than bringing a mapping back.
     * A provisioner test asserts every path here is a key it actually reads, which is what keeps
     * the two sides honest.</p>
     */
    public static String hoconPath(ConfigType type) {
        return type.name().toLowerCase(Locale.ROOT).replace(NAME_WORD_SEPARATOR, PATH_WORD_SEPARATOR);
    }

    /**
     * Every path a published file is allowed to set. The reader's side of the catalogue: anything
     * else in a file is dropped rather than merged.
     */
    public static Set<String> publishablePaths() {
        return Arrays.stream(ConfigType.values())
                .map(ScopedConfigLayout::hoconPath)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** {@code <scopeDir>/.config/jeffrey.conf} for any scope's folder */
    public static Path configFile(Path scopeDir) {
        return scopeDir.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    }

    /**
     * A temporary file to write rendered content through before renaming it onto
     * {@code configFile}.
     *
     * <p>A <em>sibling</em>, so the rename stays inside one directory and therefore one filesystem,
     * which is what makes it atomic; a temporary file in the system temp directory would turn the
     * move into a copy and reopen the torn-read window it exists to close.</p>
     *
     * <p>The {@code token} makes the name unique per publish. Two publishes of one scope can
     * overlap — an editor's save and the synchronizer's tick — and a shared temporary name would
     * let them interleave their bytes into one file that is then renamed into place. Readers
     * resolve {@link #CONFIG_FILE} by name and never list the directory, so the token's shape is
     * free.</p>
     */
    public static Path temporaryFile(Path configFile, String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return configFile.resolveSibling(configFile.getFileName() + "." + token + TEMP_SUFFIX);
    }
}
