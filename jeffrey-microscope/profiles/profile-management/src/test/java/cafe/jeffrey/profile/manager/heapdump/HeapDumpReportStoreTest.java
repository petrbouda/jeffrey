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

package cafe.jeffrey.profile.manager.heapdump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder;
import cafe.jeffrey.profile.manager.heapdump.analysis.LeakSuspectsAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.StringHeapAnalysis;
import cafe.jeffrey.profile.manager.heapdump.analysis.ThreadHeapAnalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HeapDumpReportStoreTest {

    private static final String CURRENT_LEAKS = "leak-suspects.analysis-v2.tree-v" + DominatorTreeBuilder.TREE_VERSION + ".json";

    private static final LeakSuspectsReport EMPTY_LEAKS = new LeakSuspectsReport(100, 0, List.of(), List.of());

    @TempDir
    Path analysisDir;

    @Nested
    @DisplayName("A report computed from the dominator tree")
    class TreeReport {

        @Test
        @DisplayName("is stored under the version of the tree it read")
        void isStoredUnderTheTreeVersion() {
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);

            store.write(new LeakSuspectsAnalysis(), HeapReportVersion.expected(new LeakSuspectsAnalysis()), EMPTY_LEAKS);

            assertTrue(Files.exists(analysisDir.resolve(CURRENT_LEAKS)));
            assertTrue(store.exists(new LeakSuspectsAnalysis()));
            assertEquals(EMPTY_LEAKS, store.read(new LeakSuspectsAnalysis()).orElseThrow());
        }

        @Test
        @DisplayName("cached before trees were versioned reads as not built")
        void cachedBeforeVersioningReadsAsNotBuilt() throws IOException {
            Files.writeString(analysisDir.resolve("leak-suspects.json"), "{}");
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);

            assertFalse(store.exists(new LeakSuspectsAnalysis()), "built from a tree this code would not build");
            assertTrue(store.read(new LeakSuspectsAnalysis()).isEmpty());
        }

        @Test
        @DisplayName("replaces its copies from earlier trees when written again")
        void replacesItsEarlierCopies() throws IOException {
            Files.writeString(analysisDir.resolve("leak-suspects.json"), "{}");
            Files.writeString(analysisDir.resolve("leak-suspects.tree-v1.json"), "{}");
            Files.writeString(analysisDir.resolve("biggest-objects.json"), "{}");
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);

            store.write(new LeakSuspectsAnalysis(), HeapReportVersion.expected(new LeakSuspectsAnalysis()), EMPTY_LEAKS);

            assertFalse(Files.exists(analysisDir.resolve("leak-suspects.json")));
            assertFalse(Files.exists(analysisDir.resolve("leak-suspects.tree-v1.json")));
            assertTrue(Files.exists(analysisDir.resolve(CURRENT_LEAKS)));
            assertTrue(Files.exists(analysisDir.resolve("biggest-objects.json")),
                    "another report's file is that report's to replace");
        }

        @Test
        @DisplayName("is deleted with its copies from earlier trees")
        void isDeletedWithItsEarlierCopies() throws IOException {
            Files.writeString(analysisDir.resolve("leak-suspects.json"), "{}");
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);
            store.write(new LeakSuspectsAnalysis(), HeapReportVersion.expected(new LeakSuspectsAnalysis()), EMPTY_LEAKS);
            Files.writeString(analysisDir.resolve("leak-suspects.json"), "{}");

            store.deleteAllCachedAnalyses();

            assertFalse(Files.exists(analysisDir.resolve(CURRENT_LEAKS)));
            assertFalse(Files.exists(analysisDir.resolve("leak-suspects.json")));
        }

        @Test
        @DisplayName("is deleted with the temporary file a write cut short left behind")
        void isDeletedWithAnInterruptedWrite() throws IOException {
            Path interrupted = analysisDir.resolve(CURRENT_LEAKS + ".8812736.tmp");
            Path unrelated = analysisDir.resolve("heap-dump-config.json.tmp");
            Files.writeString(interrupted, "{\"totalHeapSize\":");
            Files.writeString(unrelated, "{}");

            new HeapDumpReportStore(analysisDir).deleteAllCachedAnalyses();

            assertFalse(Files.exists(interrupted));
            assertTrue(Files.exists(unrelated), "only a report's own temporary files are its to remove");
        }
    }

    @Nested
    @DisplayName("Which reports are versioned")
    class Versioned {

        @Test
        void aNullReportPayloadIsMissingRatherThanAnException() throws IOException {
            Files.writeString(analysisDir.resolve("string-analysis.json"), "null");
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);

            assertFalse(assertDoesNotThrow(() -> store.exists(new StringHeapAnalysis())));
            assertTrue(assertDoesNotThrow(() -> store.read(new StringHeapAnalysis())).isEmpty());
        }

        @Test
        void anObservedOlderTreeCannotBeWrittenAsTheCurrentTree() {
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);
            LeakSuspectsAnalysis analysis = new LeakSuspectsAnalysis();
            HeapReportVersion olderTree = new HeapReportVersion(analysis.analysisVersion(), 1L);

            assertThrows(IllegalArgumentException.class, () -> store.write(analysis, olderTree, EMPTY_LEAKS));
            assertFalse(store.exists(analysis));
        }

        @Test
        void aReportAlgorithmVersionIsIndependentOfTheTreeVersion() {
            assertEquals("leak-suspects.analysis-v2.tree-v" + DominatorTreeBuilder.TREE_VERSION + ".json",
                    HeapDumpReportStore.storedName(new LeakSuspectsAnalysis()));
        }

        @Test
        void aCurrentTreeDoesNotMakeAnOlderReportAlgorithmCurrent() throws IOException {
            Files.writeString(analysisDir.resolve("leak-suspects.tree-v" + DominatorTreeBuilder.TREE_VERSION + ".json"), "{}");
            HeapDumpReportStore store = new HeapDumpReportStore(analysisDir);

            assertFalse(store.exists(new LeakSuspectsAnalysis()));
            assertTrue(store.read(new LeakSuspectsAnalysis()).isEmpty());
        }

        @Test
        @DisplayName("the thread report is, because it carries retained sizes whenever a tree exists")
        void theThreadReportIsVersioned() {
            assertEquals("thread-analysis.analysis-v2.tree-v" + DominatorTreeBuilder.TREE_VERSION + ".json",
                    HeapDumpReportStore.storedName(new ThreadHeapAnalysis()));
            assertEquals("thread-analysis.analysis-v2.json", HeapDumpReportStore.treelessName(new ThreadHeapAnalysis()),
                    "computed without a tree, under a name the legacy unversioned file cannot take");
        }

        @Test
        @DisplayName("a report that never reads the tree keeps its own name")
        void aReportWithoutTheTreeKeepsItsName() {
            assertEquals("string-analysis.json", HeapDumpReportStore.storedName(new StringHeapAnalysis()));
        }
    }
}
