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

package cafe.jeffrey.provider.profile.api;

import java.time.Instant;

/**
 * One citation a recommendation rests on, after checking. An ungrounded row ({@code grounded} false) is
 * stored rather than discarded: the model's mistake is exactly what the reader needs to see, and hiding
 * it would make an unverifiable report indistinguishable from a verified one.
 *
 * @param eventType   the sample event type the claim belongs to
 * @param title       the short title the model gave the recommendation
 * @param citedFrame  the frame the model cited, resolved to its measured spelling when grounded
 * @param sourcePath  the source path the model cited, or null
 * @param grounded    true when the cited frame appears in the measured call tree
 * @param sourceFound true when the cited source path exists in the analyzed source tree
 * @param selfPct     the frame's measured self share, or zero when ungrounded
 * @param totalPct    the frame's measured total share, or zero when ungrounded
 * @param generatedAt when the claim was recorded
 */
public record AdvisorClaimRow(
        String eventType,
        String title,
        String citedFrame,
        String sourcePath,
        boolean grounded,
        boolean sourceFound,
        double selfPct,
        double totalPct,
        Instant generatedAt) {
}
