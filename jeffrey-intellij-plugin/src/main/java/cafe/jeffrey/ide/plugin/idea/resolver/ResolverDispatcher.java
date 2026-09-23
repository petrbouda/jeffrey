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

package cafe.jeffrey.ide.plugin.idea.resolver;

import cafe.jeffrey.ide.plugin.idea.dto.NavigateRequest;
import com.intellij.openapi.project.Project;

/**
 * Picks the resolution strategy for a frame: obvious Kotlin FQCNs go straight to
 * {@link KotlinResolver}; everything else tries {@link JavaResolver} first, switching to Kotlin if
 * the Java path lands on a {@code .kt} file.
 */
public final class ResolverDispatcher {

    private static final String KOTLIN_EXTENSION = "kt";

    private ResolverDispatcher() {
    }

    public static Navigation resolve(Project project, NavigateRequest req) {
        if (KotlinResolver.isObviouslyKotlin(req.className())) {
            return KotlinResolver.resolve(project, req);
        }
        Navigation javaResult = JavaResolver.resolve(project, req);
        if (javaResult instanceof Navigation.Found found
                && KOTLIN_EXTENSION.equals(found.file().getExtension())) {
            return KotlinResolver.resolve(project, req);
        }
        return javaResult;
    }
}
