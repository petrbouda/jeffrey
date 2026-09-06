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
