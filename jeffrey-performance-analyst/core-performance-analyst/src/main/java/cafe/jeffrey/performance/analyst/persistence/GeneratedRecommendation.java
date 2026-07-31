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

package cafe.jeffrey.performance.analyst.persistence;

import cafe.jeffrey.profile.ai.chat.TokenUsage;
import cafe.jeffrey.performance.analyst.verification.PatchVerification;
import cafe.jeffrey.shared.common.model.Severity;

import java.time.Instant;

/**
 * A stored AI recommendation result for one (recording, sample event type): the {@code severity}
 * Jeffrey computed from the measured profile, the recommendations markdown, an optional applicable
 * patch (unified diff, {@code null} when the model proposed no code edit), what Jeffrey established
 * about that patch, and what the run consumed. The hub/workspace/project identity (+ denormalized
 * {@code projectName}) is captured so the global Overview can rank, label and deep-link to the recording
 * without resolving the remote hub.
 *
 * <p>The verification travels with the result because it cannot be recomputed later: it was established
 * against a checkout that is deleted as soon as generation finishes.</p>
 */
public record GeneratedRecommendation(
        String recordingId,
        String eventType,
        String hubId,
        String workspaceId,
        String projectId,
        String projectName,
        Severity severity,
        String recommendations,
        String patch,
        PatchVerification verification,
        TokenUsage usage,
        Instant generatedAt) {

    public GeneratedRecommendation {
        verification = verification == null ? PatchVerification.notAttempted() : verification;
        usage = usage == null ? TokenUsage.unknown() : usage;
    }
}
