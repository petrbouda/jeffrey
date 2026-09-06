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

import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Locale;

/**
 * Which files Microscope can turn into a profile on its own.
 *
 * <p>Shared by the two places that have to agree about it: the "Analyze in Microscope" action, which
 * shows itself only for such a file, and {@link AnalysableFileIconProvider}, which marks them in the
 * project tree. A file that carries the flame icon and then has no action under right-click — or the
 * reverse — is a worse answer than either rule alone.
 *
 * <p>Matched as whole suffixes rather than as an extension, because several of them are two
 * extensions deep: {@code VirtualFile.getExtension()} answers {@code lz4} for {@code run.jfr.lz4}
 * and {@code gz} for {@code heap.hprof.gz}, so an extension test hides the action on exactly the
 * files somebody compressed to keep.
 *
 * <p>The list mirrors {@code SupportedRecordingFile} on the Microscope side, minus the companion
 * artifacts — JVM and application logs, performance counters, async-profiler temp files. Those
 * import, but they describe a run rather than being one, and analysing one alone produces nothing.
 * The duplication is unavoidable: this plugin is a separate build and cannot see that enum.
 */
public final class AnalysableFiles {

    private static final List<String> ANALYSABLE_SUFFIXES = List.of(
            ".jfr",
            ".jfr.lz4",
            ".hprof",
            ".hprof.gz",
            ".pprof",
            ".pb.gz",
            ".otlp");

    private AnalysableFiles() {
    }

    public static boolean isAnalysable(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        return analysableName(file.getName());
    }

    /**
     * Whether Microscope can build a profile from a file with this name. Separate from the
     * {@link VirtualFile} check so the rule — the part that is easy to get wrong and was — can be
     * tested without an IDE fixture.
     */
    static boolean analysableName(String name) {
        // Locale.ROOT, matching Microscope: under a Turkish locale the default toLowerCase() maps I to
        // a dotless i, which would let a machine's language setting decide what the menu offers.
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : ANALYSABLE_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
