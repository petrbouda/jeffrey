/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.ide.plugin.idea;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which file names offer "Analyze in Microscope" and carry the flame icon. The rule has to agree
 * with Microscope's own {@code ManagedFile}: an action that appears and then fails on
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
        assertFalse(AnalysableFiles.analysableName("app.jvm-log"));
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
