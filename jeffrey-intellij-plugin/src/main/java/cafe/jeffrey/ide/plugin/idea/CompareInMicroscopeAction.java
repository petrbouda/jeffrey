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

import cafe.jeffrey.ide.plugin.idea.recording.RecordingPanel;
import cafe.jeffrey.ide.plugin.idea.recording.RecordingPanels;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Starts a comparison from the Project view, for the pair a developer has selected there.
 *
 * <p>Two ways to reach it, and both end in the same place — a recording panel with a baseline
 * attached, which is where a comparison is read, changed and handed on:
 *
 * <ul>
 *   <li><b>Two recordings selected.</b> The newer one opens as the primary, the older becomes its
 *       baseline.</li>
 *   <li><b>One recording selected while another's panel is open.</b> The selected file becomes that
 *       panel's baseline, the way IntelliJ's own <em>Compare File with Editor</em> reads.</li>
 * </ul>
 *
 * <p><b>No dialog asks which is which.</b> A dialog existed in the design and was cut: its only job
 * was to confirm the direction before anything happened, and the panel states the direction
 * afterwards in a strip that can flip it. Asking first is worth a window only when the answer cannot
 * be seen and changed later, and here it can.
 *
 * <p><b>The newer file is the primary</b> when two are selected — the candidate under examination
 * measured against the older baseline, which is what "did this change make it slower" means nine
 * times out of ten. Selection order would be the other candidate for that rule and is not something
 * the Project view reports reliably, so a wrong guess here is visible on the strip and one click
 * from being corrected.
 */
public final class CompareInMicroscopeAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(CompareInMicroscopeAction.class);

    /** Exactly two: three recordings have no reading, and Microscope subtracts a pair. */
    private static final int PAIR = 2;

    private static final String COMPARE_PAIR_TEXT = "Compare in Microscope";

    private static final String COMPARE_WITH_OPEN_PREFIX = "Compare with ";

    /**
     * On the EDT, unlike its sibling: deciding what this item says means asking which editors are
     * open, and the editor manager is the EDT's. The work is a walk over the open tabs.
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /**
     * Shown only when there is a pair to compare, and named after the pair it would make.
     *
     * <p>An action that is always visible and usually fails teaches a developer to ignore the menu it
     * sits in, so this one is absent unless the selection names both sides — and when the other side
     * is an open tab, the menu item says which file that is rather than making them remember.
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        List<VirtualFile> selected = recordings(event);
        if (project == null || selected.isEmpty()) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }

        if (selected.size() == PAIR) {
            event.getPresentation().setText(COMPARE_PAIR_TEXT);
            event.getPresentation().setEnabledAndVisible(true);
            return;
        }

        VirtualFile openPrimary = openPanelFileOtherThan(project, selected.getFirst());
        if (openPrimary == null) {
            event.getPresentation().setEnabledAndVisible(false);
            return;
        }
        event.getPresentation().setText(COMPARE_WITH_OPEN_PREFIX + openPrimary.getName());
        event.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        List<VirtualFile> selected = recordings(event);
        if (project == null || selected.isEmpty()) {
            return;
        }

        if (selected.size() == PAIR) {
            comparePair(project, selected);
            return;
        }
        VirtualFile openPrimary = openPanelFileOtherThan(project, selected.getFirst());
        if (openPrimary != null) {
            attach(project, openPrimary, selected.getFirst());
        }
    }

    /**
     * Opens the newer recording and measures it against the older one.
     *
     * <p>Modification time decides, and two files written in the same millisecond fall back to their
     * names, so the pair opens the same way twice rather than depending on which the platform listed
     * first.
     */
    private void comparePair(Project project, List<VirtualFile> selected) {
        List<VirtualFile> ordered = new ArrayList<>(selected);
        ordered.sort(Comparator.comparingLong(VirtualFile::getTimeStamp)
                .thenComparing(VirtualFile::getName));
        attach(project, ordered.getLast(), ordered.getFirst());
    }

    private void attach(Project project, VirtualFile primary, VirtualFile baseline) {
        RecordingPanel panel = RecordingPanels.open(project, primary);
        if (panel == null) {
            LOG.info("The recording did not open in a Microscope panel: file=" + primary.getPath());
            return;
        }
        panel.compareWith(Path.of(baseline.getPath()));
    }

    /**
     * The file behind an open recording panel that is not the one selected, or null when there is
     * none. The selected file's own panel does not count: a recording is never its own baseline.
     */
    private static VirtualFile openPanelFileOtherThan(Project project, VirtualFile selected) {
        VirtualFile open = RecordingPanels.openedPanelFile(project);
        if (open == null || open.equals(selected)) {
            return null;
        }
        return open;
    }

    /**
     * The selected files Microscope could compare — recordings only.
     *
     * <p>Heap dumps are filtered out rather than refused later: a dump compares with a dump on
     * Microscope's own heap diff page, against a different set of figures entirely, and offering the
     * action for one would promise a comparison this panel cannot make.
     */
    private static List<VirtualFile> recordings(AnActionEvent event) {
        VirtualFile[] selected = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (selected == null || selected.length == 0 || selected.length > PAIR) {
            return List.of();
        }
        List<VirtualFile> recordings = new ArrayList<>(selected.length);
        for (VirtualFile file : selected) {
            if (AnalysableFiles.isAnalysable(file) && !AnalysableFiles.isHeapDumpName(file.getName())) {
                recordings.add(file);
            }
        }
        // A pair with a dump in it is not a pair. Dropping to one file would silently offer a
        // different comparison than the one the selection asked for.
        return recordings.size() == selected.length ? recordings : List.of();
    }
}
