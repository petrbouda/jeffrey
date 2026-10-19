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

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowFrames;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionSiteStat;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysis;
import cafe.jeffrey.profile.mcp.McpFollowUp;

import java.util.Comparator;
import java.util.List;

/**
 * What {@code jvm_exceptionSites} answers: each throw site with the stack behind it, and — given the
 * caller's own packages — the frame where the caller's code entered the throw.
 * <p>
 * The caller is a coding agent sitting in the source tree, so it knows which packages are its own and
 * Jeffrey does not. It says so per call ({@code framePrefixes}); nothing is stored. A site with no
 * such frame is still listed: a timeout raised entirely inside a client library is still the
 * application's to deal with, it just has no line of the caller's to open.
 */
public final class ExceptionSites {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    /** Frames kept per stack, counted from the throwing frame down. */
    public static final int STACK_FRAMES_LIMIT = 24;

    /** Distinct messages kept per site. */
    public static final int MESSAGES_LIMIT = 5;

    private static final Comparator<Site> OWN_FRAME_FIRST =
            Comparator.comparing((Site site) -> site.ownFrame() == null)
                    .thenComparing(Comparator.comparingLong(Site::count).reversed())
                    .thenComparing(Site::thrownClass);

    private ExceptionSites() {
    }

    /**
     * What the caller asked for, its prefixes already trimmed of blanks.
     *
     * @param framePrefixes      the caller's own packages; empty when it gave none
     * @param thrownClass        only this class; null for every class
     * @param includeJvmInternal whether the JVM's own resolution and linking throws are listed too
     */
    public record Selection(List<String> framePrefixes, String thrownClass, boolean includeJvmInternal) {

        public Selection {
            framePrefixes = framePrefixes == null ? List.of() : framePrefixes.stream()
                    .filter(prefix -> prefix != null && !prefix.isBlank())
                    .map(String::strip)
                    .distinct()
                    .toList();
            thrownClass = thrownClass == null || thrownClass.isBlank() ? null : thrownClass.strip();
        }

        private boolean matches(ExceptionSiteStat site) {
            return (includeJvmInternal || site.origin() == ThrowOrigin.APPLICATION)
                    && (thrownClass == null || thrownClass.equals(site.thrownClass()));
        }
    }

    /**
     * Every site the selection keeps, in answer order: with prefixes, the sites that reach the caller's
     * code first, then by count; without, by count.
     */
    public static List<Site> select(ThrowAnalysis analysis, Selection selection) {
        List<Site> sites = analysis.sites().stream()
                .filter(selection::matches)
                .map(site -> site(site, analysis.stackOf(site), selection.framePrefixes()))
                .toList();
        if (selection.framePrefixes().isEmpty()) {
            return sites;
        }
        return sites.stream().sorted(OWN_FRAME_FIRST).toList();
    }

    private static Site site(ExceptionSiteStat site, List<ThrowFrame> frames, List<String> framePrefixes) {
        int throwingIndex = ThrowFrames.throwingFrameIndex(frames, site.thrownClass());
        List<ThrowFrame> fromThrow = frames.isEmpty() ? List.of() : frames.subList(throwingIndex, frames.size());

        Integer ownDepth = null;
        if (!framePrefixes.isEmpty()) {
            for (int depth = 0; depth < fromThrow.size(); depth++) {
                if (fromThrow.get(depth).startsWithAny(framePrefixes)) {
                    ownDepth = depth;
                    break;
                }
            }
        }

        List<ExceptionsSection.Frame> stack = fromThrow.stream()
                .limit(STACK_FRAMES_LIMIT)
                .map(ExceptionsSection.Frame::of)
                .toList();

        return new Site(
                site.thrownClass(),
                site.error(),
                site.origin(),
                ExceptionsSection.Frame.of(site.throwingFrame()),
                ownDepth == null ? null : ExceptionsSection.Frame.of(fromThrow.get(ownDepth)),
                ownDepth,
                site.count(),
                site.threadCount(),
                ExceptionsSection.messages(site.messages(), MESSAGES_LIMIT),
                site.stacktraceId(),
                stack,
                Math.max(0, fromThrow.size() - STACK_FRAMES_LIMIT));
    }

    /**
     * One page of throw sites.
     */
    public record SitesPage(
            @McpDescription("The caller's own packages the sites were matched against; empty when none were given")
            List<String> framePrefixes,
            @McpNullable
            @McpDescription("The class the sites were narrowed to; null for every class")
            String thrownClass,
            boolean includeJvmInternal,
            @McpDescription("Sampled throws the application raised, libraries included")
            long applicationThrows,
            @McpDescription("Sampled throws the JVM raised while loading, linking or reflecting")
            long jvmInternalThrows,
            @McpDescription("Sites the selection matched, across every page")
            int matchingSites,
            @McpDescription("Of the matching sites, how many have an ownFrame")
            int sitesWithOwnFrame,
            @McpDescription("With framePrefixes, the sites that reach your code first, then by count; "
                    + "otherwise by count")
            List<Site> sites,
            boolean hasMore,
            @McpNullable
            String nextCursor) {
    }

    /**
     * One throw site: an exception class and the frame that threw it, with the stack behind it.
     */
    public record Site(
            String thrownClass,
            boolean error,
            ThrowOrigin origin,
            @McpNullable
            @McpDescription("The frame that contained the throw, below the exception's constructors; "
                    + "null when no stack was recorded")
            ExceptionsSection.Frame throwingFrame,
            @McpNullable
            @McpDescription("The topmost frame matching framePrefixes - where your code entered the "
                    + "throw; null when none matches or no prefixes were given")
            ExceptionsSection.Frame ownFrame,
            @McpNullable
            @McpDescription("How many frames below throwingFrame ownFrame sits; 0 when your code threw")
            Integer ownFrameDepth,
            long count,
            int threadCount,
            @McpDescription("The " + MESSAGES_LIMIT + " commonest messages")
            List<ExceptionsSection.Message> messages,
            @McpNullable
            @McpDescription("The site's commonest stack; null when no stack was recorded")
            String stacktraceId,
            @McpDescription("That stack from throwingFrame down, at most " + STACK_FRAMES_LIMIT + " frames")
            List<ExceptionsSection.Frame> stack,
            @McpDescription("Frames of that stack below the ones listed")
            int omittedFrames) {
    }

    /**
     * What {@code jvm_exceptionSites} answers: the envelope every section shares, around the page.
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
            SitesPage dashboard,
            McpFollowUp followUp,
            @McpDescription(SectionHeader.UI_LINK)
            String uiLink) {

        public static Answer of(SectionHeader header, SitesPage page) {
            return new Answer(header.status(), header.reason(), header.profileId(), header.section(),
                    header.title(), page, header.followUp(), header.uiLink());
        }
    }
}
