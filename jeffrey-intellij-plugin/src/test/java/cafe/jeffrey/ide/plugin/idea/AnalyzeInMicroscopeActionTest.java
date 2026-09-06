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
 * Which file names offer "Analyze in Jeffrey Microscope". The rule has to agree with Microscope's own
 * {@code SupportedRecordingFile}: an action that appears and then fails on import is worse than one
 * that never appeared.
 */
public class AnalyzeInMicroscopeActionTest {

    @Test
    public void offersRecordingsAndHeapDumps() {
        assertTrue(AnalyzeInMicroscopeAction.analysableName("run.jfr"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("heap.hprof"));
    }

    /**
     * The reason this test exists. VirtualFile.getExtension() answers "lz4" and "gz" for these, so
     * matching on the extension hid the action on exactly the files somebody compressed to keep.
     */
    @Test
    public void offersTheCompressedFormsToo() {
        assertTrue(AnalyzeInMicroscopeAction.analysableName("run.jfr.lz4"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("heap.hprof.gz"));
    }

    @Test
    public void offersTheOtherRecordingFormatsMicroscopeReads() {
        assertTrue(AnalyzeInMicroscopeAction.analysableName("cpu.pprof"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("cpu.pb.gz"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("profiles.otlp"));
    }

    /**
     * Companion artifacts import, but they describe a run rather than being one — analysing a log on
     * its own produces nothing, so the action stays out of the menu for them.
     */
    @Test
    public void staysOutOfTheWayForCompanionArtifacts() {
        assertFalse(AnalyzeInMicroscopeAction.analysableName("app-jvm.log"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName("service-app.log"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName("hsperfdata"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName("run.jfr.1~"));
    }

    @Test
    public void ignoresEverythingElse() {
        assertFalse(AnalyzeInMicroscopeAction.analysableName("Main.java"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName("notes.txt"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName("jfr"));
        assertFalse(AnalyzeInMicroscopeAction.analysableName(""));
    }

    /**
     * Microscope ignores case when it recognises a file, so this does too — a heap dump saved as
     * HEAP.HPROF is the same file, and the action has to offer what the import will accept.
     */
    @Test
    public void ignoresCaseTheWayMicroscopeDoes() {
        assertTrue(AnalyzeInMicroscopeAction.analysableName("RUN.JFR"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("HEAP.HPROF"));
        assertTrue(AnalyzeInMicroscopeAction.analysableName("Run.Jfr.Lz4"));
    }
}
