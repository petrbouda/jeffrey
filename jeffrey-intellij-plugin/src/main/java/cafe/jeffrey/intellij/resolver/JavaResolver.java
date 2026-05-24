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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;

/**
 * Resolves a JVM class name (with {@code $} for nested classes) to a source location via the Java
 * PSI. Prefers the JFR-reported line when present; otherwise falls back to the named method's
 * declaration, then the class declaration. All PSI access runs inside a {@link ReadAction}.
 */
public final class JavaResolver {

    private JavaResolver() {
    }

    public static Navigation resolve(Project project, NavigateRequest req) {
        return ReadAction.compute(() -> {
            PsiClass psiClass = findClass(project, req.className());
            if (psiClass == null) {
                return new Navigation.NotFound("class-not-found");
            }
            PsiFile psiFile = psiClass.getContainingFile();
            VirtualFile vFile = psiFile == null ? null : psiFile.getVirtualFile();
            if (vFile == null) {
                return new Navigation.NotFound("no-virtual-file");
            }

            // Trust the JFR line when we have one — it is the most precise signal.
            if (req.lineNumber() > 0) {
                return new Navigation.Found(vFile, req.lineNumber() - 1, 0, Navigation.Kind.JAVA_LINE, false);
            }

            // No line: jump to the named method's declaration if we can find it.
            if (req.methodName() != null) {
                PsiMethod[] methods = psiClass.findMethodsByName(req.methodName(), false);
                if (methods.length >= 1) {
                    int line = lineForOffset(project, psiFile, methods[0].getTextOffset());
                    return new Navigation.Found(vFile, line, 0, Navigation.Kind.JAVA_PRECISE, false);
                }
            }

            // Last resort: the class declaration (imprecise).
            int line = lineForOffset(project, psiFile, psiClass.getTextOffset());
            return new Navigation.Found(vFile, line, 0, Navigation.Kind.JAVA_LINE, true);
        });
    }

    public static boolean exists(Project project, String fqcn) {
        return ReadAction.compute(() -> findClass(project, fqcn) != null);
    }

    private static PsiClass findClass(Project project, String fqcn) {
        return ClassUtil.findPsiClass(
                PsiManager.getInstance(project), fqcn, null, true, GlobalSearchScope.allScope(project));
    }

    private static int lineForOffset(Project project, PsiFile psiFile, int offset) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null || offset < 0 || offset > document.getTextLength()) {
            return 0;
        }
        return document.getLineNumber(offset);
    }
}
