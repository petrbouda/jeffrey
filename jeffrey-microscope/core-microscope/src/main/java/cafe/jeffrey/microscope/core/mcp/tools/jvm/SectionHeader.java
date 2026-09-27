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

import cafe.jeffrey.profile.mcp.McpFollowUp;

/**
 * What every {@code jvm_} section answer carries beside its dashboard: whether there is one, why not,
 * which section it is, where to go next and the page it is drawn on. Each section's answer record is
 * built from one of these and its dashboard, so the envelope is the family's contract, written once.
 *
 * @param reason why there is no dashboard; null when {@code status} is {@link SectionStatus#OK}
 * @param uiLink the section's page in the Microscope UI, for the user
 */
public record SectionHeader(
        SectionStatus status,
        String reason,
        String profileId,
        String section,
        String title,
        McpFollowUp followUp,
        String uiLink) {

    /** The descriptions each section's answer record gives the envelope's parts, in one place. */
    public static final String REASON = "Why there is no dashboard; null when status is OK";
    public static final String DASHBOARD = "The dashboard; null when status is NOT_RECORDED";
    public static final String SECTION = "The section's id, as jvm_sections lists it";
    public static final String UI_LINK = "The section's page in the Microscope UI, for the user";

    public SectionHeader {
        if (status == null || profileId == null || section == null || title == null
                || followUp == null || uiLink == null) {
            throw new IllegalArgumentException("A section header needs every part but its reason: status="
                    + status + " profileId=" + profileId + " section=" + section + " uiLink=" + uiLink);
        }
        if ((status == SectionStatus.OK) != (reason == null)) {
            throw new IllegalArgumentException(
                    "A reason goes with a missing dashboard and only then: status=" + status + " reason=" + reason);
        }
    }
}
