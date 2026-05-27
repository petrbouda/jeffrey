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
 * Turns a {@link NavigateRequest} into an actual jump in the IDE (resolution on a background read
 * action, navigation + window focus on the EDT) and serves source text for Microscope's viewer.
 */
public final class Navigator {

    private static final long STALE_THRESHOLD_MILLIS = 86_400_000L; // 1 day
    private static final String CLASS_EXTENSION = "class";

    private Navigator() {
    }

    public static NavigateResponse navigate(NavigateRequest req) {
        Project project = ProjectRegistry.findProject(req.projectId());
        if (project == null) {
            return NavigateResponse.notResolved("project-not-found");
        }
        if (!TrustedProjects.isProjectTrusted(project)) {
            return NavigateResponse.notResolved("project-not-trusted");
        }

        Navigation nav = ResolverDispatcher.resolve(project, req);
        if (nav instanceof Navigation.NotFound notFound) {
            return NavigateResponse.notResolved(notFound.reason());
        }

        Navigation.Found found = (Navigation.Found) nav;
        VirtualFile vFile = found.file();
        boolean decompiled = CLASS_EXTENSION.equals(vFile.getExtension());
        long mtime = vFile.getTimeStamp();
        String sourceMTime = Instant.ofEpochMilli(mtime).toString();
        boolean stale = isStale(mtime, req.recordingTime());

        EdtRunner.runOnEdt(() -> {
            new OpenFileDescriptor(project, vFile, found.line(), found.column()).navigate(true);
            ProjectUtil.focusProjectWindow(project, true);
        });

        return new NavigateResponse(
                true,
                found.kind().name(),
                vFile.getPath(),
                found.line() + 1,
                decompiled,
                found.imprecise(),
                stale,
                sourceMTime,
                null);
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
