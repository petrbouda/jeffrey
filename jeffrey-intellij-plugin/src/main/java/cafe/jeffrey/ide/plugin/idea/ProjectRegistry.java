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
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.ide.BuiltInServerManager;

import java.awt.Window;
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
                // One constant, on the class that serves the wire, so the version a response reports
                // cannot drift from the endpoints that actually exist.
                JeffreyMicroscopeService.PROTOCOL_VERSION,
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

    /**
     * One window as Microscope sees it in the picker.
     *
     * <p>{@code trusted} is hard-coded true rather than re-asked: the only projects that reach this
     * method are the ones {@link #openProjects()} already filtered, so a false here could not occur
     * and a window that is not trusted is absent rather than listed as untrusted.
     *
     * <p>The checkout is read per call rather than cached. It changes without the IDE being involved
     * — a branch switch on the command line moves it — and a stale branch shown next to a profile is
     * exactly the failure this field exists to prevent. It costs two small file reads.
     */
    private static ProjectInfo toProjectInfo(Project project) {
        GitHead.Checkout checkout = GitHead.read(project.getBasePath());
        return new ProjectInfo(
                project.getLocationHash(),
                project.getName(),
                project.getBasePath(),
                true,
                isFocused(project),
                checkout.branch(),
                checkout.commit());
    }

    /**
     * Whether this project's window is the one the developer is looking at. Microscope pre-selects it
     * in the picker, which is the right guess when several windows contain the class.
     */
    private static boolean isFocused(Project project) {
        Window window = WindowManager.getInstance().suggestParentWindow(project);
        return window != null && window.isActive();
    }
}
