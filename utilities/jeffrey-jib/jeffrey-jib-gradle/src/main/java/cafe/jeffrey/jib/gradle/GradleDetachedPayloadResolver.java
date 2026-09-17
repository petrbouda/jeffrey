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

import cafe.jeffrey.jib.payload.ArtifactCoordinates;
import cafe.jeffrey.jib.payload.PayloadResolutionException;
import cafe.jeffrey.jib.payload.PayloadResolver;
import com.google.cloud.tools.jib.gradle.extension.GradleData;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Resolves payload artifacts through a Gradle detached configuration, so payloads obey the build's
 * own repositories and cache.
 *
 * <p>The Gradle project is only reached on the first {@link #resolve} call. A build that bakes
 * nothing never needs it, and must not fail for lack of it.
 *
 * <p>Every Gradle call here is reflective, for the reasons {@link GradleProjects} explains. Two
 * further details make or break the detached configuration:
 *
 * <ul>
 *   <li>{@code ConfigurationContainer.detachedConfiguration} is a varargs
 *       {@code Dependency...}, whose reflective parameter type is an array class this module
 *       cannot name — it has to be built with {@link Array#newInstance}.
 *   <li>The array is one argument, not an argument list, so it is passed cast to {@code Object}.
 * </ul>
 */
final class GradleDetachedPayloadResolver implements PayloadResolver {

    private static final String PROJECT_TYPE = "org.gradle.api.Project";
    private static final String DEPENDENCY_HANDLER_TYPE = "org.gradle.api.artifacts.dsl.DependencyHandler";
    private static final String CONFIGURATION_CONTAINER_TYPE = "org.gradle.api.artifacts.ConfigurationContainer";
    private static final String CONFIGURATION_TYPE = "org.gradle.api.artifacts.Configuration";
    private static final String DEPENDENCY_TYPE = "org.gradle.api.artifacts.Dependency";

    private static final String GET_DEPENDENCIES_METHOD = "getDependencies";
    private static final String GET_CONFIGURATIONS_METHOD = "getConfigurations";
    private static final String CREATE_METHOD = "create";
    private static final String DETACHED_CONFIGURATION_METHOD = "detachedConfiguration";
    private static final String RESOLVE_METHOD = "resolve";

    private static final String CONFIGURATION_CACHE_HINT =
            "Gradle refused the resolution (this is usually the configuration cache, which JIB's "
                    + "extension hook is not compatible with). Set provisionerPath and profilerPath "
                    + "to binaries the base image already provides to build without payload resolution.";

    private final GradleData gradleData;

    GradleDetachedPayloadResolver(GradleData gradleData) {
        this.gradleData = gradleData;
    }

    @Override
    public Path resolve(ArtifactCoordinates coordinates) throws PayloadResolutionException {
        Object project = GradleProjects.project(gradleData).orElseThrow(() -> new PayloadResolutionException(
                coordinates, "no Gradle project is available to resolve it through"));
        try {
            Object dependency = createDependency(project, coordinates);
            Object configuration = detachedConfiguration(project, dependency);
            return singleFile(project, configuration, coordinates);
        } catch (PayloadResolutionException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new PayloadResolutionException(coordinates, CONFIGURATION_CACHE_HINT, e);
        }
    }

    private static Object createDependency(Object project, ArtifactCoordinates coordinates)
            throws ReflectiveOperationException {

        Object dependencies = invokeOnInterface(project, PROJECT_TYPE, GET_DEPENDENCIES_METHOD);
        Method create = GradleProjects.type(project, DEPENDENCY_HANDLER_TYPE).getMethod(CREATE_METHOD, Object.class);
        return create.invoke(dependencies, coordinates.gradleNotation());
    }

    private static Object detachedConfiguration(Object project, Object dependency)
            throws ReflectiveOperationException {

        Object configurations = invokeOnInterface(project, PROJECT_TYPE, GET_CONFIGURATIONS_METHOD);
        Object dependencyArray = Array.newInstance(GradleProjects.type(project, DEPENDENCY_TYPE), 1);
        Array.set(dependencyArray, 0, dependency);

        Method detached = GradleProjects.type(project, CONFIGURATION_CONTAINER_TYPE)
                .getMethod(DETACHED_CONFIGURATION_METHOD, dependencyArray.getClass());
        return detached.invoke(configurations, (Object) dependencyArray);
    }

    private static Path singleFile(Object project, Object configuration, ArtifactCoordinates coordinates)
            throws ReflectiveOperationException, PayloadResolutionException {

        Method resolve = GradleProjects.type(project, CONFIGURATION_TYPE).getMethod(RESOLVE_METHOD);
        Collection<?> files = (Collection<?>) resolve.invoke(configuration);
        if (files.size() != 1) {
            throw new PayloadResolutionException(
                    coordinates, "expected exactly one resolved file but Gradle returned " + files);
        }
        return ((File) files.iterator().next()).toPath();
    }

    private static Object invokeOnInterface(Object target, String interfaceName, String methodName)
            throws ReflectiveOperationException {

        return GradleProjects.type(target, interfaceName).getMethod(methodName).invoke(target);
    }
}
