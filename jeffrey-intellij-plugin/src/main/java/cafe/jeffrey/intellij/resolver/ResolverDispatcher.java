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

package cafe.jeffrey.intellij.resolver;

import cafe.jeffrey.intellij.dto.NavigateRequest;
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
