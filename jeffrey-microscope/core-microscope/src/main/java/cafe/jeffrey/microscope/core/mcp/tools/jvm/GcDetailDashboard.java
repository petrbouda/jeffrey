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

import cafe.jeffrey.microscope.mcp.protocol.McpNullable;

import java.util.List;

/**
 * What {@code jvm_gcDetail} shows: the pages there are, and at most one of them rendered.
 * <p>
 * One component per page rather than one open object, so every page reaches the wire typed; exactly
 * the component named by {@code page} is set, and the rest are null. With no page asked for, only the
 * list is.
 *
 * @param pages the pages there are, the names the tool's {@code page} input takes
 * @param page  the page shown; null when only the list was asked for
 */
public record GcDetailDashboard(
        List<GcDetailPage> pages,
        @McpNullable
        GcDetailPage page,
        @McpNullable
        GcDetailPages.Configuration configuration,
        @McpNullable
        GcDetailPages.Tenuring tenuring,
        @McpNullable
        GcDetailPages.Ihop ihop,
        @McpNullable
        GcDetailPages.G1 g1,
        @McpNullable
        GcDetailPages.Zgc zgc,
        @McpNullable
        GcDetailPages.StringTables stringTables,
        @McpNullable
        GcDetailPages.Finalizers finalizers,
        @McpNullable
        GcDetailPages.References references,
        @McpNullable
        GcDetailPages.Phases phases,
        @McpNullable
        GcDetailPages.Plab plab) {

    private static final List<GcDetailPage> PAGES = List.of(GcDetailPage.values());

    /** The list of pages alone. */
    static GcDetailDashboard listing() {
        return new Builder(null).build();
    }

    /** A dashboard showing one page, which that page fills in. */
    static Builder of(GcDetailPage page) {
        return new Builder(page);
    }

    /** Collects the one page a dashboard shows. */
    static final class Builder {

        private final GcDetailPage page;
        private GcDetailPages.Configuration configuration;
        private GcDetailPages.Tenuring tenuring;
        private GcDetailPages.Ihop ihop;
        private GcDetailPages.G1 g1;
        private GcDetailPages.Zgc zgc;
        private GcDetailPages.StringTables stringTables;
        private GcDetailPages.Finalizers finalizers;
        private GcDetailPages.References references;
        private GcDetailPages.Phases phases;
        private GcDetailPages.Plab plab;

        private Builder(GcDetailPage page) {
            this.page = page;
        }

        void configuration(GcDetailPages.Configuration value) {
            this.configuration = value;
        }

        void tenuring(GcDetailPages.Tenuring value) {
            this.tenuring = value;
        }

        void ihop(GcDetailPages.Ihop value) {
            this.ihop = value;
        }

        void g1(GcDetailPages.G1 value) {
            this.g1 = value;
        }

        void zgc(GcDetailPages.Zgc value) {
            this.zgc = value;
        }

        void stringTables(GcDetailPages.StringTables value) {
            this.stringTables = value;
        }

        void finalizers(GcDetailPages.Finalizers value) {
            this.finalizers = value;
        }

        void references(GcDetailPages.References value) {
            this.references = value;
        }

        void phases(GcDetailPages.Phases value) {
            this.phases = value;
        }

        void plab(GcDetailPages.Plab value) {
            this.plab = value;
        }

        GcDetailDashboard build() {
            return new GcDetailDashboard(PAGES, page, configuration, tenuring, ihop, g1, zgc, stringTables,
                    finalizers, references, phases, plab);
        }
    }
}
