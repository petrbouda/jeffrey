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

package cafe.jeffrey.ide.plugin.idea.recording;

import cafe.jeffrey.ide.plugin.idea.AnalysableFiles;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The recordings this project holds, for the menu that picks one to compare against.
 *
 * <p>Asked of the platform's own file-type index rather than by walking directories: the index is
 * what the IDE already maintains, it respects the project's excluded folders, and it costs nothing
 * to consult. A recording sitting in {@code target/} or {@code node_modules} is therefore not
 * offered, which is the same judgement the Project view makes about what belongs to the project.
 *
 * <p><b>Heap dumps are left out.</b> A dump compares with a dump, on Microscope's own heap diff page
 * and against a different set of figures entirely; offering one here would produce a pair whose only
 * possible verdict is that it cannot be compared.
 */
public final class RecordingsInProject {

    private RecordingsInProject() {
    }

    /**
     * Every recording in the project except the one already open, ordered by name so the menu does
     * not reshuffle itself between openings.
     *
     * <p>Runs in smart mode, so it blocks while the IDE is indexing — call it off the EDT. An
     * unindexed project answering "no recordings" would be worse than a menu that arrives late: the
     * developer would read it as Jeffrey not having found the file they can see in the tree.
     */
    public static List<Path> find(Project project, Path exclude) {
        DumbService.getInstance(project).waitForSmartMode();
        Collection<VirtualFile> files = ReadAction.compute(() ->
                FileTypeIndex.getFiles(RecordingFileType.INSTANCE, GlobalSearchScope.projectScope(project)));

        List<Path> recordings = new ArrayList<>(files.size());
        for (VirtualFile file : files) {
            if (file.isDirectory() || AnalysableFiles.isHeapDumpName(file.getName())) {
                continue;
            }
            Path path = Path.of(file.getPath());
            if (!path.equals(exclude)) {
                recordings.add(path);
            }
        }
        recordings.sort(Comparator.comparing(Path::toString));
        return List.copyOf(recordings);
    }
}
