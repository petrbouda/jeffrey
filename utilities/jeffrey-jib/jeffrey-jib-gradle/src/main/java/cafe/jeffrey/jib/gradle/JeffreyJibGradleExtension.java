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

package cafe.jeffrey.jib.gradle;

import cafe.jeffrey.jib.JeffreyBuildPlanExtender;
import cafe.jeffrey.jib.JeffreyJibConfig;
import cafe.jeffrey.jib.WorkDirectories;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * JIB Gradle plugin extension that wraps the image entrypoint so Jeffrey profiling is initialised
 * before the app starts, without forcing operators to override the container {@code command:} in
 * Kubernetes YAML, and bakes the provisioner and async-profiler into the image.
 *
 * <p>Recommended consumer setup (Gradle, Kotlin DSL) — uses the string {@code properties} DSL
 * so nothing needs to import {@link JeffreyJibConfig} on the build script's compile classpath:
 * <pre>{@code
 * jib {
 *   pluginExtensions {
 *     pluginExtension {
 *       implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
 *       properties = mapOf(
 *         "payloadVersion" to "0.14.0",
 *         "jeffreyHome" to "/mnt/azure/runtime/shared/jeffrey",
 *         "baseConfig" to "/jeffrey/jeffrey-base.conf",
 *         "overrideConfig" to "/jeffrey/jeffrey-overrides.conf",
 *       )
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Supported properties:
 * <ul>
 *   <li>{@code enabled} — build-time kill switch. Defaults to {@code true}. Set to
 *       {@code "false"} to skip wrapping entirely — the produced image is identical to one
 *       built without this extension. Useful for conditionally disabling profiling per
 *       environment without ripping the extension out of the build file.
 *   <li>{@code payloadVersion} — <strong>required</strong> whenever a payload is baked: the
 *       jeffrey-jib release whose payload artifacts the image carries, normally the same version
 *       as this extension.
 *   <li>{@code provisionerSource} — {@code native} (default) or {@code jar}; which build of the
 *       provisioner to bake. The provisioner itself is always baked and its path is not
 *       configurable: it writes the layout Jeffrey Hub reads.
 *   <li>{@code jeffreyHome} — root of the shared volume the application writes its recordings to.
 *   <li>{@code baseConfig} — default {@code /jeffrey/jeffrey-base.conf}.
 *   <li>{@code overrideConfig} — optional per-deploy override, default
 *       {@code /jeffrey/jeffrey-overrides.conf}.
 *   <li>{@code profilerPath} — the image already carries async-profiler; that payload is neither
 *       resolved nor baked.
 *   <li>{@code argFile} — location of the generated JVM argfile, default {@code /tmp/jvm.args}.
 *   <li>{@code projectName} — Jeffrey project name baked as {@code JEFFREY_PROJECT_NAME};
 *       defaults to the Gradle project name. Pod-level env still overrides it.
 * </ul>
 *
 * <p>The typed {@code configuration(Action<JeffreyJibConfig>) { … }} DSL is also accepted, but
 * requires {@link JeffreyJibConfig} to be on the script's compile classpath and works only on
 * Gradle versions whose {@code ObjectFactory.newInstance(type, project)} call matches a
 * {@code JeffreyJibConfig} constructor — use the {@code properties} form above for portability.
 *
 * <p>Payloads are resolved through the project's repositories (not the {@code buildscript}
 * ones) with a detached configuration, at task execution time — which is why this extension,
 * like JIB's extension hook itself, is not compatible with the configuration cache.
 */
public class JeffreyJibGradleExtension implements JibGradlePluginExtension<JeffreyJibConfig> {

    @Override
    public Optional<Class<JeffreyJibConfig>> getExtraConfigType() {
        return Optional.of(JeffreyJibConfig.class);
    }

    @Override
    public ContainerBuildPlan extendContainerBuildPlan(
            ContainerBuildPlan buildPlan,
            Map<String, String> properties,
            Optional<JeffreyJibConfig> config,
            GradleData gradleData,
            ExtensionLogger logger) throws JibPluginExtensionException {

        JeffreyJibConfig effective = config.orElseGet(JeffreyJibConfig::new);
        JeffreyBuildPlanExtender.applyProperties(effective, properties, logger);

        Optional<Object> project = GradleProjects.project(gradleData);

        // Default the Jeffrey project name to the Gradle project name — baked as the
        // JEFFREY_PROJECT_NAME image ENV, it makes the image self-identifying so the
        // container needs no config file and no per-pod env for the common case.
        // A pod-level JEFFREY_PROJECT_NAME still overrides the baked default.
        if (effective.getProjectName() == null) {
            project.flatMap(GradleProjects::name).ifPresent(effective::setProjectName);
        }

        return new JeffreyBuildPlanExtender(
                getClass(), new GradleDetachedPayloadResolver(gradleData), workDirectory(project, logger))
                .extend(buildPlan, effective, logger);
    }

    /** {@code build/jeffrey-jib}, so JIB sees the same payload paths on every build. */
    private Path workDirectory(Optional<Object> project, ExtensionLogger logger)
            throws JibPluginExtensionException {

        Optional<Path> buildDirectory = project.flatMap(GradleProjects::buildDirectory);
        if (buildDirectory.isPresent()) {
            return WorkDirectories.under(buildDirectory.get());
        }
        return WorkDirectories.temporary(getClass(), logger);
    }
}
