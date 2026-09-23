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
import cafe.jeffrey.jib.WorkDirectories;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.maven.extension.JibMavenPluginExtension;
import com.google.cloud.tools.jib.maven.extension.MavenData;
import com.google.cloud.tools.jib.plugins.extension.ExtensionLogger;
import com.google.cloud.tools.jib.plugins.extension.JibPluginExtensionException;
import org.apache.maven.project.MavenProject;

import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * JIB Maven plugin extension that wraps the image entrypoint so Jeffrey profiling is initialised
 * before the app starts, without forcing operators to override the container {@code command:} in
 * Kubernetes YAML, and bakes the provisioner and async-profiler into the image.
 *
 * <p>Consumer side (Maven). The plugin dependency is a <em>flavour</em> of the extension —
 * {@code jeffrey-jib-maven-jar} or {@code jeffrey-jib-maven-native} — which brings this class and
 * the payload jar carrying the matching provisioner build plus async-profiler. Nothing else is
 * required:
 * <pre>{@code
 * <plugin>
 *   <groupId>com.google.cloud.tools</groupId>
 *   <artifactId>jib-maven-plugin</artifactId>
 *   <dependencies>
 *     <dependency>
 *       <groupId>cafe.jeffrey-analyst</groupId>
 *       <artifactId>jeffrey-jib-maven-jar</artifactId>
 *       <version>jib.version</version>
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
 *
 * <p>The payload jar reaches the extension like any other plugin dependency, through the build's
 * {@code <pluginRepositories>}, mirrors, proxies and local repository.
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

        Optional<MavenProject> project = mavenProject(mavenData);

        // Default the Jeffrey project name to the Maven artifactId — baked as the
        // JEFFREY_PROJECT_NAME image ENV, it makes the image self-identifying so the
        // container needs no config file and no per-pod env for the common case.
        // A pod-level JEFFREY_PROJECT_NAME still overrides the baked default.
        if (effective.getProjectName() == null) {
            project.map(MavenProject::getArtifactId).ifPresent(effective::setProjectName);
        }

        return new JeffreyBuildPlanExtender(getClass(), getClass().getClassLoader(), workDirectory(project, logger))
                .extend(buildPlan, effective, logger);
    }

    private static Optional<MavenProject> mavenProject(MavenData mavenData) {
        if (mavenData == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mavenData.getMavenProject());
    }

    /** {@code target/jeffrey-jib}, so JIB sees the same payload paths on every build. */
    private Path workDirectory(Optional<MavenProject> project, ExtensionLogger logger)
            throws JibPluginExtensionException {

        if (project.isPresent()) {
            return WorkDirectories.under(Path.of(project.get().getBuild().getDirectory()));
        }
        return WorkDirectories.temporary(getClass(), logger);
    }
}
