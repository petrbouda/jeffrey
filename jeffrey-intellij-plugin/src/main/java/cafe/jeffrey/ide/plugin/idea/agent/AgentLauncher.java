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

package cafe.jeffrey.ide.plugin.idea.agent;

import com.intellij.openapi.project.Project;

import java.nio.file.Path;

/**
 * Runs an agent's command line somewhere the developer can see it.
 *
 * <p>An interface with two implementations rather than one method with a branch, so the one that
 * touches the Terminal plugin's classes is never loaded on an IDE where that plugin is switched off.
 */
public interface AgentLauncher {

    void launch(Project project, Path workingDirectory, String command);
}
