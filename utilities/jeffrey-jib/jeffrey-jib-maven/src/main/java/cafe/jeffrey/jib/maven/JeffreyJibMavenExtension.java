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

package cafe.jeffrey.jib.maven;

import cafe.jeffrey.jib.JeffreyBuildPlanExtender;
import cafe.jeffrey.jib.JeffreyJibConfig;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/**
 * JIB Maven plugin extension that wraps the image entrypoint so Jeffrey profiling is initialised
 * before the app starts, without forcing operators to override the container {@code command:} in
 * Kubernetes YAML.
 *
 * <p>Consumer side (Maven):
 * <pre>{@code
 * <plugin>
 *   <groupId>com.google.cloud.tools</groupId>
 *   <artifactId>jib-maven-plugin</artifactId>
 *   <dependencies>
 *     <dependency>
 *       <groupId>cafe.jeffrey</groupId>
 *       <artifactId>jeffrey-jib-maven</artifactId>
 *       <version>${jeffrey.version}</version>
 *     </dependency>
 *   </dependencies>
 *   <configuration>
 *     <pluginExtensions>
 *       <pluginExtension>
 *         <implementation>cafe.jeffrey.jib.maven.JeffreyJibMavenExtension</implementation>
 *         <configuration implementation="cafe.jeffrey.jib.JeffreyJibConfig">
 *           <jeffreyHome>/mnt/azure/runtime/shared/jeffrey</jeffreyHome>
 *           <baseConfig>/jeffrey/jeffrey-base.conf</baseConfig>
 *         </configuration>
 *       </pluginExtension>
 *     </pluginExtensions>
 *   </configuration>
 * </plugin>
 * }</pre>
 */
@Named
@Singleton
public class JeffreyJibMavenExtension implements JibMavenPluginExtension<JeffreyJibConfig> {

    @Override
    public Optional<Class<JeffreyJibConfig>> getExtraConfigType() {
        return Optional.of(JeffreyJibConfig.class);
    }

    @Override
    public ContainerBuildPlan extendContainerBuildPlan(
            ContainerBuildPlan buildPlan,
            Map<String, String> properties,
            Optional<JeffreyJibConfig> config,
            MavenData mavenData,
            ExtensionLogger logger) throws JibPluginExtensionException {

        JeffreyJibConfig effective = config.orElseGet(JeffreyJibConfig::new);
        JeffreyBuildPlanExtender.applyProperties(effective, properties, logger);
        return new JeffreyBuildPlanExtender(getClass()).extend(buildPlan, effective, logger);
    }
}
