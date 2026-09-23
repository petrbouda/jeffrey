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

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Result of resolving a stack frame to a source location. {@code line}/{@code column} are 0-based
 * (as {@code OpenFileDescriptor} expects).
 */
public sealed interface Navigation permits Navigation.Found, Navigation.NotFound {

    enum Kind {
        JAVA_PRECISE,
        JAVA_LINE,
        KOTLIN_LINE,
        KOTLIN_FALLBACK
    }

    record Found(VirtualFile file, int line, int column, Kind kind, boolean imprecise) implements Navigation {
    }

    record NotFound(String reason) implements Navigation {
    }
}
