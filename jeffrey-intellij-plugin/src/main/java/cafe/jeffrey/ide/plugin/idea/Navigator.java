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

import cafe.jeffrey.ide.plugin.idea.dto.NavigateRequest;
import cafe.jeffrey.ide.plugin.idea.dto.NavigateResponse;
import cafe.jeffrey.ide.plugin.idea.dto.SourceResponse;
import cafe.jeffrey.ide.plugin.idea.resolver.Navigation;
import cafe.jeffrey.ide.plugin.idea.resolver.ResolverDispatcher;
import cafe.jeffrey.ide.plugin.idea.util.EdtRunner;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;

import java.time.Instant;

/**
 * Turns a {@link NavigateRequest} into a source location, and — for {@link #navigate} — into an
 * actual jump in the IDE (resolution on a background read action, navigation + window focus on the
 * EDT). Also serves source text for Microscope's viewer.
 *
 * <p>Resolving and jumping are separate entry points because they have separate callers. A person
 * clicking a frame wants the editor to move; an agent asking where a frame lives wants the file and
 * the line and emphatically does not want the developer's editor to jump under their hands while
 * they are working. {@link #resolve} answers the question, {@link #navigate} answers it and then
 * acts on it, and both report it in the same shape so Microscope parses one thing.
 */
public final class Navigator {

    private static final long STALE_THRESHOLD_MILLIS = 86_400_000L; // 1 day
    private static final String CLASS_EXTENSION = "class";

    private Navigator() {
    }

    /**
     * Where a frame lives, without touching the editor. Nothing here has a side effect the developer
     * can see, which is what makes it safe to call from an automated reader on every frame it is
     * about to write about.
     */
    public static NavigateResponse resolve(NavigateRequest req) {
        Located located = locate(req);
        return located.response();
    }

    /**
     * The same resolution, followed by opening the file and bringing the window to the front.
     */
    public static NavigateResponse navigate(NavigateRequest req) {
        Located located = locate(req);
        if (located.found() == null) {
            return located.response();
        }

        Project project = located.project();
        Navigation.Found found = located.found();
        EdtRunner.runOnEdt(() -> {
            new OpenFileDescriptor(project, found.file(), found.line(), found.column()).navigate(true);
            ProjectUtil.focusProjectWindow(project, true);
        });
        return located.response();
    }

    private static Located locate(NavigateRequest req) {
        Project project = ProjectRegistry.findProject(req.projectId());
        if (project == null) {
            return Located.notResolved("project-not-found");
        }
        if (!TrustedProjects.isProjectTrusted(project)) {
            return Located.notResolved("project-not-trusted");
        }

        Navigation nav = ResolverDispatcher.resolve(project, req);
        if (nav instanceof Navigation.NotFound notFound) {
            return Located.notResolved(notFound.reason());
        }

        Navigation.Found found = (Navigation.Found) nav;
        VirtualFile vFile = found.file();
        boolean decompiled = CLASS_EXTENSION.equals(vFile.getExtension());
        long mtime = vFile.getTimeStamp();

        NavigateResponse response = new NavigateResponse(
                true,
                found.kind().name(),
                vFile.getPath(),
                found.line() + 1,
                decompiled,
                found.imprecise(),
                isStale(mtime, req.recordingTime()),
                Instant.ofEpochMilli(mtime).toString(),
                null);
        return new Located(project, found, response);
    }

    public static SourceResponse fetchSource(Project project, String className) {
        if (project == null) {
            return SourceResponse.notResolved("project-not-found");
        }
        if (!TrustedProjects.isProjectTrusted(project)) {
            return SourceResponse.notResolved("project-not-trusted");
        }
        return ReadAction.compute(() -> {
            PsiClass psiClass = ClassUtil.findPsiClass(
                    PsiManager.getInstance(project), className, null, true, GlobalSearchScope.allScope(project));
            if (psiClass == null) {
                return SourceResponse.notResolved("class-not-found");
            }
            // Prefer attached sources over the decompiled .class: getNavigationElement() returns the
            // source element when sources are attached, and the compiled element itself otherwise.
            PsiFile psiFile = psiClass.getNavigationElement().getContainingFile();
            if (psiFile == null) {
                return SourceResponse.notResolved("no-source-file");
            }
            VirtualFile vFile = psiFile.getVirtualFile();
            String path = vFile == null ? null : vFile.getPath();
            boolean decompiled = vFile != null && CLASS_EXTENSION.equals(vFile.getExtension());
            return new SourceResponse(true, psiFile.getText(), path, decompiled, null);
        });
    }

    /**
     * A resolution and everything needed to act on it. {@code found} is null exactly when the
     * response says unresolved, so a caller that only reports has one field to read and a caller
     * that also opens the file has the project and the location it needs without resolving twice.
     */
    private record Located(Project project, Navigation.Found found, NavigateResponse response) {

        static Located notResolved(String reason) {
            return new Located(null, null, NavigateResponse.notResolved(reason));
        }
    }

    private static boolean isStale(long mtimeMillis, String recordingTime) {
        if (recordingTime == null) {
            return false;
        }
        try {
            long recorded = Instant.parse(recordingTime).toEpochMilli();
            return mtimeMillis > recorded + STALE_THRESHOLD_MILLIS;
        } catch (Exception e) {
            return false;
        }
    }
}
