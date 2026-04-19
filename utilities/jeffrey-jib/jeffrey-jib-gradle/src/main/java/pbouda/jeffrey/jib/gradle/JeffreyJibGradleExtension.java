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

package pbouda.jeffrey.jib.gradle;

import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.gradle.extension.GradleData;
import com.google.cloud.tools.jib.gradle.extension.JibGradlePluginExtension;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import pbouda.jeffrey.jib.JeffreyBuildPlanExtender;
import pbouda.jeffrey.jib.JeffreyJibConfig;

import java.util.Map;
import java.util.Optional;

/**
 * JIB Gradle plugin extension that wraps the image entrypoint so Jeffrey profiling is initialised
 * before the app starts, without forcing operators to override the container {@code command:} in
 * Kubernetes YAML.
 *
 * <p>Consumer side (Gradle, Kotlin DSL):
 * <pre>{@code
 * jib {
 *   pluginExtensions {
 *     pluginExtension {
 *       implementation = "pbouda.jeffrey.jib.gradle.JeffreyJibGradleExtension"
 *       configuration(Action<JeffreyJibConfig> {
 *         enabled = project.hasProperty("jeffreyProfiling")
 *         jeffreyHome = "/mnt/azure/runtime/shared/jeffrey"
 *         baseConfig = "/jeffrey/jeffrey-base.conf"
 *         overrideConfig = "/jeffrey/jeffrey-overrides.conf"
 *       })
 *     }
 *   }
 * }
 * }</pre>
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
        return new JeffreyBuildPlanExtender(getClass()).extend(buildPlan, effective, logger);
    }
}
