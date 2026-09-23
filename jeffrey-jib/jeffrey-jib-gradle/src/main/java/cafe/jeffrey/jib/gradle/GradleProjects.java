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

import com.google.cloud.tools.jib.gradle.extension.GradleData;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reflective access to the Gradle {@code Project} behind JIB's {@link GradleData}.
 *
 * <p>This module is built with Maven, where the Gradle API is not a compile dependency — at
 * runtime inside a Gradle build the types are always present. Every method is looked up on the
 * Gradle <em>interface</em>, not on the instance's class: Gradle hands back decorated subclasses
 * whose declaring class is often not public, and invoking a method found on one of those fails
 * with an access error.
 *
 * <p>Nothing here throws. Every caller can live without the answer — the default project name,
 * the build directory — and treats an empty result as "use the fallback".
 */
abstract class GradleProjects {

    private static final String PROJECT_TYPE = "org.gradle.api.Project";
    private static final String PROJECT_LAYOUT_TYPE = "org.gradle.api.file.ProjectLayout";
    private static final String DIRECTORY_PROPERTY_TYPE = "org.gradle.api.file.DirectoryProperty";
    private static final String PROVIDER_TYPE = "org.gradle.api.provider.Provider";

    private static final String GET_PROJECT_METHOD = "getProject";
    private static final String GET_NAME_METHOD = "getName";
    private static final String GET_LAYOUT_METHOD = "getLayout";
    private static final String GET_BUILD_DIRECTORY_METHOD = "getBuildDirectory";
    private static final String GET_AS_FILE_METHOD = "getAsFile";
    private static final String GET_METHOD = "get";

    /** {@code gradleData.getProject()}, empty when JIB handed over no data or no project. */
    static Optional<Object> project(GradleData gradleData) {
        if (gradleData == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(GradleData.class.getMethod(GET_PROJECT_METHOD).invoke(gradleData));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** {@code project.getName()}. */
    static Optional<String> name(Object project) {
        try {
            Object name = invoke(project, PROJECT_TYPE, GET_NAME_METHOD);
            return Optional.ofNullable(name).map(Object::toString);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * {@code project.getLayout().getBuildDirectory().getAsFile().get()} — the modern spelling;
     * {@code Project.getBuildDir()} was removed in Gradle 9.
     */
    static Optional<Path> buildDirectory(Object project) {
        try {
            Object layout = invoke(project, PROJECT_TYPE, GET_LAYOUT_METHOD);
            Object buildDirectory = invoke(layout, PROJECT_LAYOUT_TYPE, GET_BUILD_DIRECTORY_METHOD);
            Object asFile = invoke(buildDirectory, DIRECTORY_PROPERTY_TYPE, GET_AS_FILE_METHOD);
            Object file = invoke(asFile, PROVIDER_TYPE, GET_METHOD);
            return Optional.of(((File) file).toPath());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Object invoke(Object target, String interfaceName, String methodName)
            throws ReflectiveOperationException {

        Class<?> type = Class.forName(interfaceName, false, target.getClass().getClassLoader());
        return type.getMethod(methodName).invoke(target);
    }
}
