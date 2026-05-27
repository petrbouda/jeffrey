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

package cafe.jeffrey.ide.plugin.idea;

import cafe.jeffrey.ide.plugin.idea.dto.InstanceResponse;
import cafe.jeffrey.ide.plugin.idea.dto.ProjectInfo;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.ide.BuiltInServerManager;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Describes this IDE instance for the {@code /api/jeffrey/instance} endpoint and resolves a target
 * window by {@code locationHash}. No discovery file is written: with no auth token to deliver,
 * Microscope finds instances purely by scanning the built-in-server port range and calling
 * {@code /instance} — so this is a plain in-memory holder of stable instance identity.
 */
@Service(Service.Level.APP)
public final class ProjectRegistry {

    private static final int PROTOCOL_VERSION = 1;

    private final String instanceId = UUID.randomUUID().toString();
    private final String startedAt = Instant.now().toString();
    private final long pid = ProcessHandle.current().pid();

    public static ProjectRegistry getInstance() {
        return ApplicationManager.getApplication().getService(ProjectRegistry.class);
    }

    public InstanceResponse currentInstance() {
        ApplicationInfo appInfo = ApplicationInfo.getInstance();
        ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();
        return new InstanceResponse(
                PROTOCOL_VERSION,
                instanceId,
                names.getFullProductName(),
                appInfo.getBuild().getProductCode(),
                appInfo.getFullVersion(),
                pid,
                BuiltInServerManager.getInstance().getPort(),
                startedAt,
                openProjects());
    }

    private static List<ProjectInfo> openProjects() {
        // Untrusted projects are omitted entirely — they never appear in Microscope's picker.
        return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                .filter(TrustedProjects::isProjectTrusted)
                .map(ProjectRegistry::toProjectInfo)
                .toList();
    }

    /** Looks up the open project with the given {@code locationHash}, or null if none/closed. */
    public static Project findProject(String projectId) {
        if (projectId == null) {
            return null;
        }
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (projectId.equals(project.getLocationHash())) {
                return project;
            }
        }
        return null;
    }

    private static ProjectInfo toProjectInfo(Project project) {
        // TODO: focused via WindowManager, vcsBranch via Git4Idea (best-effort, later).
        return new ProjectInfo(
                project.getLocationHash(),
                project.getName(),
                project.getBasePath(),
                true,
                false,
                null);
    }
}
