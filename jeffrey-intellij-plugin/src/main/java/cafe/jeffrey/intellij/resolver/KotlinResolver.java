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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Line-based resolution for Kotlin frames (no method-level precision). Kotlin compiles synthetic
 * class names ({@code FooKt}, {@code Foo$Companion}, {@code Foo$bar$1}, ...); we strip to the outer
 * class, resolve it via the Java PSI, and jump to the JFR line. When the outer class is a top-level
 * {@code *Kt} facade that PSI can't resolve, we fall back to a filename search for {@code Foo.kt}.
 *
 * <p>Deliberately uses only platform + Java-PSI APIs so it needs no dependency on the Kotlin plugin.
 */
public final class KotlinResolver {

    private static final Set<String> KOTLIN_MARKERS =
            Set.of("$Companion", "$DefaultImpls", "$$inlined$", "$$serializer", "$lambda$", "$WhenMappings");
    private static final String KT_SUFFIX = "Kt";
    private static final String KT_EXTENSION = "kt";

    private KotlinResolver() {
    }

    /** FQCN-only signals that a frame is almost certainly Kotlin. */
    public static boolean isObviouslyKotlin(String fqcn) {
        if (fqcn == null) {
            return false;
        }
        if (fqcn.endsWith(KT_SUFFIX)) {
            return true;
        }
        for (String marker : KOTLIN_MARKERS) {
            if (fqcn.contains(marker)) {
                return true;
            }
        }
        return hasAnonymousSuffix(fqcn);
    }

    /** Matches a {@code $} followed by a digit, e.g. {@code Foo$1} (anonymous / lambda). */
    private static boolean hasAnonymousSuffix(String fqcn) {
        int dollar = fqcn.indexOf('$');
        while (dollar >= 0 && dollar + 1 < fqcn.length()) {
            if (Character.isDigit(fqcn.charAt(dollar + 1))) {
                return true;
            }
            dollar = fqcn.indexOf('$', dollar + 1);
        }
        return false;
    }

    public static Navigation resolve(Project project, NavigateRequest req) {
        return ReadAction.compute(() -> {
            String fqcn = req.className();
            int dollar = fqcn.indexOf('$');
            String outer = dollar >= 0 ? fqcn.substring(0, dollar) : fqcn;

            VirtualFile vFile = null;
            Navigation.Kind kind = Navigation.Kind.KOTLIN_LINE;

            PsiClass psiClass = ClassUtil.findPsiClass(
                    PsiManager.getInstance(project), outer, null, true, GlobalSearchScope.allScope(project));
            if (psiClass != null) {
                // Prefer attached sources over the decompiled .class (see JavaResolver).
                PsiFile psiFile = psiClass.getNavigationElement().getContainingFile();
                vFile = psiFile == null ? null : psiFile.getVirtualFile();
            } else if (outer.endsWith(KT_SUFFIX)) {
                vFile = findByFilename(project, outer);
                kind = Navigation.Kind.KOTLIN_FALLBACK;
            }

            if (vFile == null) {
                return new Navigation.NotFound("kotlin-class-not-found");
            }

            int line = req.lineNumber() > 0 ? req.lineNumber() - 1 : 0;
            boolean imprecise = req.lineNumber() <= 0;
            return new Navigation.Found(vFile, line, 0, kind, imprecise);
        });
    }

    /** Maps a top-level facade {@code com.acme.UtilsKt} to a {@code Utils.kt} file. */
    private static VirtualFile findByFilename(Project project, String facadeFqcn) {
        String simple = facadeFqcn.substring(facadeFqcn.lastIndexOf('.') + 1);
        String fileBase = simple.substring(0, simple.length() - KT_SUFFIX.length());
        Collection<VirtualFile> candidates = FilenameIndex.getVirtualFilesByName(
                fileBase + "." + KT_EXTENSION, GlobalSearchScope.allScope(project));
        return candidates.stream().findFirst().orElse(null);
    }
}
