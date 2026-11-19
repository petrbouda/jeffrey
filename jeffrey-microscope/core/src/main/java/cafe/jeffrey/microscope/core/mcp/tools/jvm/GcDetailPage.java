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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.MicroscopeView;

import java.util.function.BiConsumer;

/**
 * The garbage-collection pages beneath the {@code jvm_gc} overview, in the order a reader meets them.
 * {@code jvm_gcDetail} takes one of these constants and lists the pages by the same names, so a value
 * from one answer is the input to the next.
 * <p>
 * Each page names the Microscope page it is drawn on and renders itself into the dashboard, so the
 * names the tool advertises and the work behind them cannot drift apart.
 */
public enum GcDetailPage {

    CONFIGURATION(MicroscopeView.GARBAGE_COLLECTION_CONFIGURATION,
            (source, page) -> page.configuration(GcDetailPages.configuration(source))),
    TENURING(MicroscopeView.GARBAGE_COLLECTION,
            (source, page) -> page.tenuring(GcDetailPages.tenuring(source))),
    IHOP(MicroscopeView.GARBAGE_COLLECTION,
            (source, page) -> page.ihop(GcDetailPages.ihop(source))),
    G1(MicroscopeView.GARBAGE_COLLECTION_G1,
            (source, page) -> page.g1(GcDetailPages.g1(source))),
    ZGC(MicroscopeView.GARBAGE_COLLECTION_ZGC,
            (source, page) -> page.zgc(GcDetailPages.zgc(source))),
    STRING_TABLES(MicroscopeView.STRING_SYMBOL_TABLES,
            (source, page) -> page.stringTables(GcDetailPages.stringTables(source))),
    FINALIZERS(MicroscopeView.MEMORY_FINALIZERS,
            (source, page) -> page.finalizers(GcDetailPages.finalizers(source))),
    REFERENCES(MicroscopeView.MEMORY_REFERENCE_PROCESSING,
            (source, page) -> page.references(GcDetailPages.references(source))),
    PHASES(MicroscopeView.GARBAGE_COLLECTION,
            (source, page) -> page.phases(GcDetailPages.phases(source))),
    PLAB(MicroscopeView.GARBAGE_COLLECTION,
            (source, page) -> page.plab(GcDetailPages.plab(source)));

    private final MicroscopeView view;
    private final BiConsumer<GcDetailPages.Source, GcDetailDashboard.Builder> render;

    GcDetailPage(MicroscopeView view, BiConsumer<GcDetailPages.Source, GcDetailDashboard.Builder> render) {
        this.view = view;
        this.render = render;
    }

    /** The Microscope page this one is drawn on. */
    public MicroscopeView view() {
        return view;
    }

    void render(GcDetailPages.Source source, GcDetailDashboard.Builder page) {
        render.accept(source, page);
    }
}
