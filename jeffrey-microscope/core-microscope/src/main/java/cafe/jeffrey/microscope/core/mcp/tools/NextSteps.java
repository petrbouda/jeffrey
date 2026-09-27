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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where the next answer lives, carried back beside the figures.
 * <p>
 * A model picks its next call from what it has just read, and the tool description that would have
 * told it was read many turns earlier. {@code JvmSection} established this for the machine-level
 * dashboards; this is the same envelope for every other family.
 * <p>
 * The rule these lines follow, unchanged from that one: <em>they route, they never diagnose.</em> A
 * line says what this answer cannot tell you and which tool can. None of them claims the figures
 * above are bad.
 * <p>
 * {@link Builder#nextWhen} and {@link Builder#guidanceWhen} are the one concession, and it is not a
 * threshold. They gate a line on a phenomenon having <em>occurred</em> — a pool timed out, a request failed — the same kind of
 * question {@code JvmSections.isAvailable} already asks about recorded event types. "It happened, and
 * here is what explains it" is still routing; "it happened too often" would be a verdict, and no line
 * here is allowed to make one.
 * <p>
 * An answer's follow-up is an {@link McpFollowUp}: the calls to make next as {@link McpNextTool}s,
 * ready to send, and {@code guidance} for advice that is not a call. A payload record embeds it as
 * one component, {@code McpFollowUp followUp}. {@link #builder(AdvertisedFamilies)} builds it and
 * leaves out a call whose tool belongs to a withheld family, asking {@link AdvertisedFamilies#servesTool}
 * — the same test {@code AdvertisedFamilies.hint} makes — so an answer never points at a tool this
 * installation does not serve. A guidance line that routes to another family still goes through
 * {@code hint}, and the empty line it becomes is left out.
 */
public final class NextSteps {

    private NextSteps() {
    }

    /**
     * A builder of an answer's follow-up, gated on the families this installation advertises.
     */
    public static Builder builder(AdvertisedFamilies advertised) {
        if (advertised == null) {
            throw new IllegalArgumentException("advertised families must not be null");
        }
        return new Builder(advertised);
    }

    /**
     * An answer's follow-up: the calls to make next, each dropped when its tool is not served, and the
     * advice that is not a call.
     */
    public static final class Builder {

        private final AdvertisedFamilies advertised;
        private final List<McpNextTool> nextTools = new ArrayList<>();
        private final List<String> guidance = new ArrayList<>();

        private Builder(AdvertisedFamilies advertised) {
            this.advertised = advertised;
        }

        /**
         * A call that belongs on every answer of this kind; left out when its tool is not served.
         */
        public Builder next(McpNextTool tool) {
            if (advertised.servesTool(tool.tool())) {
                nextTools.add(tool);
            }
            return this;
        }

        /**
         * A call that belongs only when the thing it talks about actually happened.
         */
        public Builder nextWhen(boolean occurred, McpNextTool tool) {
            if (occurred) {
                next(tool);
            }
            return this;
        }

        /**
         * A call that belongs only when the thing it talks about happened, built only then: for a call
         * whose arguments exist only when it did, so the guard and the construction cannot disagree.
         */
        public Builder nextWhen(boolean occurred, Supplier<McpNextTool> tool) {
            if (occurred) {
                next(tool.get());
            }
            return this;
        }

        /**
         * Advice that is not a tool call, passed through as written; an empty line is left out.
         */
        public Builder guidance(String line) {
            if (!line.isBlank()) {
                guidance.add(line);
            }
            return this;
        }

        /**
         * Advice that belongs only when the thing it talks about actually happened.
         */
        public Builder guidanceWhen(boolean occurred, String line) {
            if (occurred) {
                guidance(line);
            }
            return this;
        }

        /**
         * Advice that belongs only when the thing it talks about happened, written only then: for a line
         * formatted from a value that exists only when it did.
         */
        public Builder guidanceWhen(boolean occurred, Supplier<String> line) {
            if (occurred) {
                guidance(line.get());
            }
            return this;
        }

        /**
         * Advice that routes to another family, kept only where that family is served - the same gate a
         * call gets, for a line that names a tool rather than calling it.
         */
        public Builder guidanceFor(String family, String line) {
            if (advertised.has(family)) {
                guidance(line);
            }
            return this;
        }

        /**
         * The calls and the advice, for the payload's {@code followUp} component.
         */
        public McpFollowUp followUp() {
            return new McpFollowUp(nextTools, guidance);
        }
    }
}
