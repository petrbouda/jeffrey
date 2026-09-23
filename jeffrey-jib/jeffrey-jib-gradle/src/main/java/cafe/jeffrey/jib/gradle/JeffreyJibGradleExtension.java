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
 * <p>Recommended consumer setup (Gradle, Kotlin DSL). The {@code buildscript} dependency is a
 * <em>flavour</em> of the extension — {@code jeffrey-jib-gradle-jar} or
 * {@code jeffrey-jib-gradle-native} — which brings this class and the payload jar carrying the
 * matching provisioner build plus async-profiler. The string {@code properties} DSL keeps
 * {@link JeffreyJibConfig} off the build script's compile classpath:
 * <pre>{@code
 * buildscript {
 *   dependencies {
 *     classpath("cafe.jeffrey-analyst:jeffrey-jib-gradle-jar:0.14.0")
 *   }
 * }
 *
 * jib {
 *   pluginExtensions {
 *     pluginExtension {
 *       implementation = "cafe.jeffrey.jib.gradle.JeffreyJibGradleExtension"
 *       properties = mapOf(
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
 *   <li>{@code jeffreyHome} — root of the shared volume the application writes its recordings to.
 *   <li>{@code baseConfig} — default {@code /jeffrey/jeffrey-base.conf}.
 *   <li>{@code overrideConfig} — optional per-deploy override, default
 *       {@code /jeffrey/jeffrey-overrides.conf}.
 *   <li>{@code profilerPath} — the image already carries async-profiler; that payload is not
 *       baked.
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
 * <p>The provisioner build is not a property: it is chosen by which flavour the build script puts
 * on the {@code buildscript} classpath, and its path is not configurable because it writes the
 * layout Jeffrey Hub reads.
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

        return new JeffreyBuildPlanExtender(getClass(), getClass().getClassLoader(), workDirectory(project, logger))
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
