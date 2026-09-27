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
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingSpan;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.Set;

/**
 * The garbage-collection pages beneath the overview.
 * <p>
 * {@code jvm_gc} answers the question almost everyone has — what did collection cost, and was it the
 * young generation or the old. These are the pages a reader reaches for once that answer is not
 * enough: the tenuring distribution behind a survivor-space decision, the IHOP behind a concurrent
 * cycle starting too late, the region composition behind an evacuation failure, the allocation stalls
 * behind a ZGC pause. Each is a real page in the Jeffrey UI and none of them belongs in an overview.
 * <p>
 * They are one tool taking a name rather than ten tools, because a reader asks for at most one of them
 * and only after {@code jvm_gc} has pointed there. Ten more entries in every {@code tools/list} would
 * cost every session to serve the few that go this deep — and most are collector-specific, so a G1
 * recording has nothing to say about half of them.
 * <p>
 * Several of these carry a row per collection, so each is cut to its first rows and says how many it
 * left out ({@link GcDetailPages}): a recording with ten thousand collections would otherwise render a
 * document nobody can read.
 */
public record GcDetailSection(ProfileManager profileManager) implements JvmSection<GcDetailDashboard> {

    public static final String ID = "gcDetail";

    private static final String TITLE = "Garbage Collection — detail";

    private static final Set<Type> EVENT_TYPES = Set.of(
            Type.GARBAGE_COLLECTION,
            Type.YOUNG_GARBAGE_COLLECTION,
            Type.OLD_GARBAGE_COLLECTION,
            Type.G1_GARBAGE_COLLECTION,
            Type.Z_YOUNG_GARBAGE_COLLECTION,
            Type.Z_OLD_GARBAGE_COLLECTION);

    private static final String ALLOCATION_PATHS_WHY =
            "names the code that produced the garbage; these pages explain how collection behaved, never "
                    + "what allocated";
    private static final String COLLECTOR_WHY =
            "reports which collector this recording used; a collector-specific page on another collector "
                    + "is empty rather than wrong";
    private static final String PAGE_WHY = "renders this page; most pages are collector-specific";
    private static final String FLAGS_WHY =
            "separates a setting somebody chose from one the JVM's ergonomics picked";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public Set<Type> eventTypes() {
        return EVENT_TYPES;
    }

    /** The collection overview the pages sit beneath; each page names its own. */
    @Override
    public MicroscopeView view() {
        return MicroscopeView.GARBAGE_COLLECTION;
    }

    /** A page's own Microscope page; the overview while only the list is shown. */
    @Override
    public MicroscopeView view(GcDetailDashboard dashboard) {
        return dashboard.page() == null ? view() : dashboard.page().view();
    }

    /**
     * The list routes to each page by the name it lists; a page routes to what it cannot explain.
     */
    @Override
    public void followUp(NextSteps.Builder next, GcDetailDashboard dashboard) {
        String profileId = profileManager.info().id();
        if (dashboard.page() == null) {
            for (GcDetailPage page : dashboard.pages()) {
                next.next(SectionCalls.on(SectionCalls.JVM_GC_DETAIL, profileId)
                        .with(SectionCalls.PAGE, page)
                        .why(PAGE_WHY));
            }
            return;
        }
        SectionCalls.allocationPaths(next, profileManager, ALLOCATION_PATHS_WHY);
        next.next(SectionCalls.on(SectionCalls.JVM_GC, profileId).why(COLLECTOR_WHY))
                .next(SectionCalls.on(SectionCalls.JVM_FLAGS, profileId).why(FLAGS_WHY));
    }

    /**
     * With no page asked for, the answer is which pages there are — the same shape
     * {@code jvm_configuration} uses, so a reader who has met one already knows this one.
     */
    @Override
    public GcDetailDashboard render() {
        return GcDetailDashboard.listing();
    }

    /**
     * The one page, beside the list of pages.
     */
    public GcDetailDashboard page(GcDetailPage page) {
        GcDetailDashboard.Builder dashboard = GcDetailDashboard.of(page);
        page.render(new GcDetailPages.Source(profileManager.gcManager(), RecordingSpan.of(profileManager.info())),
                dashboard);
        return dashboard.build();
    }

    /**
     * What {@code jvm_gcDetail} answers: the envelope every section shares, around this section's dashboard.
     */
    public record Answer(
            SectionStatus status,
            @McpNullable
            @McpDescription(SectionHeader.REASON)
            String reason,
            String profileId,
            @McpDescription(SectionHeader.SECTION)
            String section,
            String title,
            @McpNullable
            @McpDescription(SectionHeader.DASHBOARD)
            GcDetailDashboard dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, GcDetailDashboard dashboard) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), dashboard, header.followUp(), header.uiLink());
        }
    }
}
