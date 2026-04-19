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

package pbouda.jeffrey.jib;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger.LogLevel;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtension;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core build-plan transformation shared by the Maven and Gradle JIB plugin extensions.
 *
 * <p>The extender installs a shell wrapper at {@code /usr/local/bin/jeffrey-entrypoint} into a new
 * image layer, promotes that wrapper to the image ENTRYPOINT, and moves JIB's auto-derived java
 * command ({@code java -cp @/app/jib-classpath-file <MainClass>}) into CMD. At container start the
 * wrapper runs {@code jeffrey-cli init} (resolved from {@code $JEFFREY_HOME/libs/current/}) and
 * then execs the original command with the CLI-produced argfile prepended.
 *
 * <p>Non-null string fields on the {@link JeffreyJibConfig} are baked as image-level ENV
 * defaults on the build plan (wrapper reads them at runtime; Kubernetes pod env still wins).
 * If {@code config.isEnabled() == false}, the build plan is returned unchanged — image has
 * no wrapper and no Jeffrey layer.
 */
public final class JeffreyBuildPlanExtender {

    private static final String ENTRYPOINT_RESOURCE = "/pbouda/jeffrey/jib/jeffrey-entrypoint.sh";
    private static final AbsoluteUnixPath ENTRYPOINT_PATH =
            AbsoluteUnixPath.get("/usr/local/bin/jeffrey-entrypoint");
    private static final FilePermissions EXEC_PERMS = FilePermissions.fromOctalString("755");
    private static final String LAYER_NAME = "jeffrey-entrypoint";

    private final Class<? extends JibPluginExtension> extensionClass;

    public JeffreyBuildPlanExtender(Class<? extends JibPluginExtension> extensionClass) {
        this.extensionClass = extensionClass;
    }

    /**
     * Transforms the incoming build plan by adding the wrapper layer, rewriting the entrypoint,
     * and baking any requested image-level ENV defaults.
     *
     * @param buildPlan JIB-produced build plan (must already have an entrypoint — we move it to cmd)
     * @param config    user configuration (never null; pass {@code new JeffreyJibConfig()} for defaults)
     * @param logger    JIB extension logger for build-time messages
     * @return the transformed build plan (or the input unchanged when {@code config.enabled == false})
     * @throws JibPluginExtensionException if the wrapper script cannot be extracted from the
     *                                     classpath, or if JIB did not produce an entrypoint
     */
    public ContainerBuildPlan extend(
            ContainerBuildPlan buildPlan,
            JeffreyJibConfig config,
            ExtensionLogger logger) throws JibPluginExtensionException {

        if (!config.isEnabled()) {
            logger.log(LogLevel.LIFECYCLE, "jeffrey-jib: disabled (enabled=false); build plan unchanged");
            return buildPlan;
        }

        List<String> originalEntrypoint = buildPlan.getEntrypoint();
        if (originalEntrypoint == null || originalEntrypoint.isEmpty()) {
            throw new JibPluginExtensionException(
                    extensionClass,
                    "JIB did not produce an entrypoint. The Jeffrey JIB extension wraps JIB's "
                            + "auto-derived java command; let JIB set the mainClass and do not "
                            + "override container.entrypoint.");
        }

        Path wrapperScript = extractEntrypointScript();
        FileEntriesLayer wrapperLayer = FileEntriesLayer.builder()
                .setName(LAYER_NAME)
                .addEntry(wrapperScript, ENTRYPOINT_PATH, EXEC_PERMS)
                .build();

        Map<String, String> extraEnv = collectEnvDefaults(config);

        logger.log(LogLevel.LIFECYCLE,
                "jeffrey-jib: wrapping entrypoint with " + ENTRYPOINT_PATH
                        + "; original entrypoint moved to CMD: " + originalEntrypoint
                        + (extraEnv.isEmpty() ? "" : "; image ENV defaults: " + extraEnv));

        ContainerBuildPlan.Builder builder = buildPlan.toBuilder()
                .addLayer(wrapperLayer)
                .setEntrypoint(List.of(ENTRYPOINT_PATH.toString()))
                .setCmd(originalEntrypoint);

        // Merge our env on top of whatever the build plan already had (user-set JIB
        // container.environment survives; our keys win only for the specific keys we set).
        if (!extraEnv.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(buildPlan.getEnvironment());
            merged.putAll(extraEnv);
            builder.setEnvironment(merged);
        }

        return builder.build();
    }

    private static Map<String, String> collectEnvDefaults(JeffreyJibConfig config) {
        Map<String, String> env = new LinkedHashMap<>();
        putIfPresent(env, "JEFFREY_HOME", config.getJeffreyHome());
        putIfPresent(env, "JEFFREY_BASE_CONFIG", config.getBaseConfig());
        putIfPresent(env, "JEFFREY_OVERRIDE_CONFIG", config.getOverrideConfig());
        putIfPresent(env, "JEFFREY_CLI_PATH", config.getCliPath());
        putIfPresent(env, "JEFFREY_ARG_FILE", config.getArgFile());
        return env;
    }

    private static void putIfPresent(Map<String, String> env, String key, String value) {
        if (value != null && !value.isEmpty()) {
            env.put(key, value);
        }
    }

    private Path extractEntrypointScript() throws JibPluginExtensionException {
        try (InputStream in = JeffreyBuildPlanExtender.class.getResourceAsStream(ENTRYPOINT_RESOURCE)) {
            if (in == null) {
                throw new JibPluginExtensionException(
                        extensionClass,
                        "jeffrey-entrypoint.sh not found on classpath at " + ENTRYPOINT_RESOURCE);
            }
            Path tempDir = Files.createTempDirectory("jeffrey-jib-");
            tempDir.toFile().deleteOnExit();
            Path target = tempDir.resolve("jeffrey-entrypoint.sh");
            Files.copy(in, target);
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException e) {
            throw new JibPluginExtensionException(
                    extensionClass, "Failed to extract jeffrey-entrypoint.sh from classpath", e);
        }
    }
}
