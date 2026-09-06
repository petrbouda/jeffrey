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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which file names offer "Analyze in Microscope" and carry the flame icon. The rule has to agree
 * with Microscope's own {@code SupportedRecordingFile}: an action that appears and then fails on
 * import is worse than one that never appeared.
 */
public class AnalysableFilesTest {

    @Test
    public void offersRecordingsAndHeapDumps() {
        assertTrue(AnalysableFiles.analysableName("run.jfr"));
        assertTrue(AnalysableFiles.analysableName("heap.hprof"));
    }

    /**
     * The reason this test exists. VirtualFile.getExtension() answers "lz4" and "gz" for these, so
     * matching on the extension hid the action on exactly the files somebody compressed to keep.
     */
    @Test
    public void offersTheCompressedFormsToo() {
        assertTrue(AnalysableFiles.analysableName("run.jfr.lz4"));
        assertTrue(AnalysableFiles.analysableName("heap.hprof.gz"));
    }

    @Test
    public void offersTheOtherRecordingFormatsMicroscopeReads() {
        assertTrue(AnalysableFiles.analysableName("cpu.pprof"));
        assertTrue(AnalysableFiles.analysableName("cpu.pb.gz"));
        assertTrue(AnalysableFiles.analysableName("profiles.otlp"));
    }

    /**
     * Companion artifacts import, but they describe a run rather than being one — analysing a log on
     * its own produces nothing, so the action stays out of the menu for them.
     */
    @Test
    public void staysOutOfTheWayForCompanionArtifacts() {
        assertFalse(AnalysableFiles.analysableName("app-jvm.log"));
        assertFalse(AnalysableFiles.analysableName("service-app.log"));
        assertFalse(AnalysableFiles.analysableName("hsperfdata"));
        assertFalse(AnalysableFiles.analysableName("run.jfr.1~"));
    }

    @Test
    public void ignoresEverythingElse() {
        assertFalse(AnalysableFiles.analysableName("Main.java"));
        assertFalse(AnalysableFiles.analysableName("notes.txt"));
        assertFalse(AnalysableFiles.analysableName("jfr"));
        assertFalse(AnalysableFiles.analysableName(""));
    }

    /**
     * Microscope ignores case when it recognises a file, so this does too — a heap dump saved as
     * HEAP.HPROF is the same file, and the action has to offer what the import will accept.
     */
    @Test
    public void ignoresCaseTheWayMicroscopeDoes() {
        assertTrue(AnalysableFiles.analysableName("RUN.JFR"));
        assertTrue(AnalysableFiles.analysableName("HEAP.HPROF"));
        assertTrue(AnalysableFiles.analysableName("Run.Jfr.Lz4"));
    }
}
